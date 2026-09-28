# 第 10 掌 · 路由命中率专项验收（售前 / 售后各 10 组真实样本）
# 用法：应用已启动，脚本自建用户与项目，然后逐条打到 /workflow/ask 上比对分支
param(
    [string]$base = 'http://127.0.0.1:8080',
    [string]$tag  = 'routing'
)
$ErrorActionPreference = 'Stop'
$outDir = $PSScriptRoot

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

$user = 'route-verify-' + (Get-Date -Format 'HHmmss')
$null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
$token = (Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json).token
$project = Post-Json "$base/api/projects" @{
    name = 'route-verify'; title = '路由验收'; themeColor = '#2F6BFF'
    openingLine = '你好。'; closingLine = '再见。'; status = 'ACTIVE'
    systemPrompt = '你是数字人小流。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
$projectId = $project.id
$null = Post-Json "$base/api/projects/$projectId/documents" @{
    docName = '数字人产品手册.md'
    content = '本产品支持 SaaS 与私有化部署两种方式。SaaS 按坐席每月 199 元起，私有化部署按项目报价，通常 15 个工作日起。提供 14 天免费试用。深圳展厅可预约参观，工作日九点到十八点开放，周一闭馆。'
} @{ 'X-Token' = $token }
Write-Host "[OK] 项目=$projectId"

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
    $a = Post-Json "$base/api/projects/$projectId/workflow/ask" @{ question = $q; sessionId = 'route-p' } | ConvertFrom-Json
    $ok = ($a.branch -eq 'presale')
    if ($ok) { $hit++ }
    $rows += "presale`t$($a.branch)`t$ok`t$q"
    Write-Host "[售前] 期望=presale 实际=$($a.branch) $(if ($ok) {'OK'} else {'MISS'}) — $q"
}
foreach ($q in $aftersale) {
    $a = Post-Json "$base/api/projects/$projectId/workflow/ask" @{ question = $q; sessionId = 'route-a' } | ConvertFrom-Json
    $ok = ($a.branch -eq 'aftersale')
    if ($ok) { $hit++ }
    $rows += "aftersale`t$($a.branch)`t$ok`t$q"
    Write-Host "[售后] 期望=aftersale 实际=$($a.branch) $(if ($ok) {'OK'} else {'MISS'}) — $q"
}
Write-Utf8 (Join-Path $outDir "$tag-routing.tsv") ($rows -join "`n")
Write-Host "路由命中：$hit / 20"
