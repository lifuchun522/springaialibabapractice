# 第 10 掌真实验收脚本（PowerShell，UTF-8 with BOM）
# 用真实 DeepSeek + MySQL + MCP Server 跑一遍编排层，落证据到 evidence-ch10/
param(
    [string]$base = 'http://127.0.0.1:8080',
    [string]$tag  = 'run'
)
$ErrorActionPreference = 'Stop'
$outDir = $PSScriptRoot
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

function Post-Json($url, $obj, $headers) {
    $file = Join-Path $env:TEMP ('req-' + [guid]::NewGuid().ToString('N') + '.json')
    Write-Utf8 $file ($obj | ConvertTo-Json -Depth 6)
    $a = @('-s', '-X', 'POST', $url, '-H', 'Content-Type: application/json; charset=utf-8', '--noproxy', '*', '-d', "@$file")
    if ($headers) { foreach ($k in $headers.Keys) { $a += @('-H', "$k`: $($headers[$k])") } }
    $resp = & curl.exe @a
    Remove-Item $file -Force
    return $resp
}

# ---- 注册 / 登录 / 建项目 / 灌知识 ----
$user = 'flow-verify-' + (Get-Date -Format 'HHmmss')
$null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
$login = Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json
$token = $login.token
$project = Post-Json "$base/api/projects" @{
    name         = 'flow-verify'
    title        = '编排真实验收'
    themeColor   = '#2F6BFF'
    openingLine  = '你好。'
    closingLine  = '再见。'
    status       = 'ACTIVE'
    systemPrompt = '你是数字人小流，回答简短口语化。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
$projectId = $project.id
$null = Post-Json "$base/api/projects/$projectId/documents" @{
    docName = '数字人产品手册.md'
    content = '本产品支持 SaaS 与私有化部署两种方式。SaaS 按坐席每月 199 元起，私有化部署按项目报价，通常 15 个工作日起。提供 14 天免费试用，试用不需要信用卡。深圳展厅可预约参观，工作日九点到十八点开放，周一闭馆。'
} @{ 'X-Token' = $token }
Write-Host "[OK] 用户=$user 项目=$projectId 知识已入库"

function Ask($path, $question, $sessionId, $name) {
    $r = Post-Json "$base/api/projects/$projectId/workflow/$path" @{ question = $question; sessionId = $sessionId }
    Write-Utf8 (Join-Path $outDir "$tag-$name.json") $r
    return ($r | ConvertFrom-Json)
}

function Show($label, $answer) {
    $nodes = ($answer.nodes | ForEach-Object { "$($_.node)($($_.elapsedMs)ms)" }) -join ' → '
    Write-Host "--- $label ---"
    Write-Host "branch=$($answer.branch) nodes=$nodes"
    Write-Host "reply=$($answer.reply)"
}

# ---- 验收 1：顺序模式，同一问题 20 次，节点序列必须完全一致 ----
$sequences = @()
for ($i = 1; $i -le 20; $i++) {
    $a = Ask 'sequential' '我这台 X1 送修大概要多久？' 'flow-seq' "acc1-run$i"
    $sequences += ($a.sequence -join ',')
}
$distinct = @($sequences | Select-Object -Unique)
Write-Utf8 (Join-Path $outDir "$tag-acc1-sequences.txt") (($sequences | ForEach-Object { $_ }) -join "`n")
Write-Host "--- 验收 1：20 次调用，不同节点序列数 = $($distinct.Count) ---"
Write-Host "节点序列：$($distinct[0])"

# ---- 验收 2：并行模式两条分支各有独立产出 ----
$a2 = Ask 'parallel' '展厅这周三还有位置吗？顺便说说参观预约政策。' 'flow-par' 'acc2-parallel'
Show '验收 2 并行' $a2
Write-Host "outputs 键：$(($a2.outputs.PSObject.Properties.Name) -join ', ')"
Write-Host "knowledge_hits=$($a2.outputs.knowledge_hits)"
Write-Host "biz_records=$($a2.outputs.biz_records)"

# ---- 验收 3：售前 / 售后各 10 组样本，路由命中要全部正确 ----
$presale = @(
    '你们的数字人支持私有化部署吗？',
    'SaaS 版一个月多少钱？',
    '有没有免费试用？要绑信用卡吗？',
    '我们公司想做数字人，怎么选型？',
    '可以来深圳展厅参观一下吗？',
    '你们的产品和别家比有什么优势？',
    '私有化部署大概要多久上线？',
    '想了解一下你们的报价方案',
    '试用期是多久？',
    '你们支持哪些部署方式？'
)
$aftersale = @(
    '展厅这周三下午还有空位吗？',
    '我要查一下我的预约还在不在',
    '展馆周一开门吗？',
    '帮我看看 2026-10-03 上午的余位',
    '我买的服务怎么还没生效？',
    '预约之后怎么改时间？',
    '你们展厅几点关门？',
    '这台设备送修大概要多久？',
    '我想问下我的工单进度',
    '深圳展厅还有停车位吗？'
)

$rows = @()
$hit = 0
foreach ($q in $presale) {
    $a = Ask 'ask' $q 'flow-route-presale' ('acc3-pre-' + [Math]::Abs($q.GetHashCode()))
    $ok = ($a.branch -eq 'presale')
    if ($ok) { $hit++ }
    $rows += "presale`t$($a.branch)`t$ok`t$q"
    Write-Host "[售前] 期望=presale 实际=$($a.branch) $(if ($ok) {'OK'} else {'MISS'}) — $q"
}
foreach ($q in $aftersale) {
    $a = Ask 'ask' $q 'flow-route-aftersale' ('acc3-aft-' + [Math]::Abs($q.GetHashCode()))
    $ok = ($a.branch -eq 'aftersale')
    if ($ok) { $hit++ }
    $rows += "aftersale`t$($a.branch)`t$ok`t$q"
    Write-Host "[售后] 期望=aftersale 实际=$($a.branch) $(if ($ok) {'OK'} else {'MISS'}) — $q"
}
Write-Utf8 (Join-Path $outDir "$tag-acc3-routing.tsv") ($rows -join "`n")
Write-Host "--- 验收 3：路由命中 $hit / 20 ---"

# ---- 验收 4：节点名与耗时（上面每次输出里都已包含） ----
$a4 = Ask 'sequential' '随便问一句，看看节点埋点' 'flow-trace' 'acc4-trace'
Show '验收 4 埋点' $a4

# ---- 补信息循环 ----
$a5 = Ask 'loop' '帮我查一下还有没有位置' 'flow-loop' 'acc5-loop'
Show '循环模式' $a5

Write-Host "PROJECT_ID=$projectId"
Write-Host "TOKEN=$token"
