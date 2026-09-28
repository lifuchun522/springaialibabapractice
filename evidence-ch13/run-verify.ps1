# 第 13 掌真实验收（PowerShell，UTF-8 with BOM）
# 前提：两个独立的知识 Agent 进程已启动（8082 / 8083）+ 主应用（8080）
param([string]$base = 'http://127.0.0.1:8080')
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

# ---- 1) 两个进程各自的能力声明（证明它们是独立进程、各自有数据） ----
foreach ($port in 8082, 8083) {
    $card = & curl.exe -s --noproxy '*' "http://127.0.0.1:$port/.well-known/agent.json"
    Write-Utf8 (Join-Path $outDir "card-$port.json") $card
    Write-Host "[能力声明 $port] $card"
}

# ---- 2) 各自灌各自的知识（数据属于它们，主服务没有这张表） ----
$null = Post-Json 'http://127.0.0.1:8082/knowledge/documents' @{ docName = '展厅手册.md'; content = '深圳展厅工作日九点到十八点开放，周一闭馆，参观免费。团体讲解需提前三天预约。' }
$null = Post-Json 'http://127.0.0.1:8083/knowledge/documents' @{ docName = '产品手册.md'; content = '本产品支持 SaaS 与私有化部署，SaaS 每月 199 元起，私有化按项目报价。' }
Write-Host "[OK] 两个知识 Agent 各自灌了自己的文档"

# ---- 3) 建项目，连续调用主服务，观察多实例被分散调用 ----
$user = 'a2a-verify-' + (Get-Date -Format 'HHmmss')
$null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
$token = (Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json).token
$project = Post-Json "$base/api/projects" @{
    name = 'a2a-verify'; title = 'A2A 验收'; themeColor = '#2F6BFF'
    openingLine = '你好。'; closingLine = '再见。'; status = 'ACTIVE'; systemPrompt = '你是数字人小跨。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
$projectId = $project.id
Write-Host "[OK] 项目=$projectId"

$instances = & curl.exe -s --noproxy '*' "$base/api/projects/$projectId/a2a/instances"
Write-Utf8 (Join-Path $outDir 'instances.json') $instances
Write-Host "[实例列表] $instances"

$rows = @()
for ($i = 1; $i -le 4; $i++) {
    $resp = Post-Json "$base/api/projects/$projectId/a2a/ask" @{ question = '展厅的开放时间是什么？' }
    Write-Utf8 (Join-Path $outDir "ask-$i.json") $resp
    $j = $resp | ConvertFrom-Json
    $rows += "$i`t$($j.instanceId)`t$($j.taskId)`t$($j.traceId)`t$($j.deltas)`t$($j.elapsedMs)"
    Write-Host "[调用 $i] instance=$($j.instanceId) task=$($j.taskId) trace=$($j.traceId) deltas=$($j.deltas) elapsedMs=$($j.elapsedMs)"
    Write-Host "         answer=$($j.answer)"
}
Write-Utf8 (Join-Path $outDir 'calls.tsv') ("序号`t实例`t任务`t追踪号`t流式片段`t耗时ms`n" + ($rows -join "`n"))

# ---- 4) 版本不兼容：主服务侧改协议版本，必须显式失败且不重试 ----
$badFile = Join-Path $env:TEMP ('bad-' + [guid]::NewGuid().ToString('N') + '.json')
Write-Utf8 $badFile '{\"question\":\"x\"}'
$bad = & curl.exe -s -o (Join-Path $outDir 'version-mismatch-body.txt') -w '%{http_code}' --noproxy '*' -X POST 'http://127.0.0.1:8082/a2a/tasks' -H 'Content-Type: application/json; charset=utf-8' -H 'A2A-Version: 9.9' -d "@$badFile"
Remove-Item $badFile -Force
Write-Host "[版本不兼容] HTTP $bad（409 = 显式拒绝，而不是静默给出错答案）"
