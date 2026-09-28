# 第 11 掌真实验收小工具（PowerShell，UTF-8 with BOM）
# 用法：
#   .\run-1-run.ps1                      # 建项目 + 灌知识 + 发起高风险售后请求（应中断）
#   .\run-2-state.ps1 -ThreadId xxx      # 查检查点历史（卡在哪一步）
#   .\run-3-resume.ps1 -ThreadId xxx     # 人工批准后恢复
param(
    [string]$base = 'http://127.0.0.1:8080',
    [string]$tag = 'run',
    [string]$ThreadId = '',
    [string]$Decision = 'APPROVE',
    [switch]$Run,
    [switch]$State,
    [switch]$Resume
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

function Get-Json($url) {
    return (& curl.exe -s --noproxy '*' $url)
}

$stateFile = Join-Path $outDir "$tag-project.json"
if (Test-Path $stateFile) {
    $saved = Get-Content $stateFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $projectId = $saved.projectId
    $token = $saved.token
    Write-Host "[OK] 复用项目=$projectId"
} else {
    $user = 'graph-verify-' + (Get-Date -Format 'HHmmss')
    $null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
    $token = (Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json).token
    $project = Post-Json "$base/api/projects" @{
        name = 'graph-verify'; title = '售后图验收'; themeColor = '#2F6BFF'
        openingLine = '你好。'; closingLine = '再见。'; status = 'ACTIVE'
        systemPrompt = '你是数字人小图。'
    } @{ 'X-Token' = $token } | ConvertFrom-Json
    $projectId = $project.id
    $null = Post-Json "$base/api/projects/$projectId/documents" @{
        docName = '售后政策.md'
        content = '签收后 7 天内可无理由退款，已拆封商品需人工审核。送修需提供设备序列号，返修时效 5 到 7 个工作日。展厅参观免费，工作日九点到十八点开放，周一闭馆。'
    } @{ 'X-Token' = $token }
    Write-Utf8 $stateFile (@{ projectId = $projectId; token = $token } | ConvertTo-Json)
    Write-Host "[OK] 新建项目=$projectId，知识已入库"
}

if ($Run) {
    $body = @{ question = '我的订单 A20240617 想退款，已经签收了'; sessionId = 'graph-s1' }
    $resp = Post-Json "$base/api/projects/$projectId/after-sale/run" $body
    Write-Utf8 (Join-Path $outDir "$tag-run.json") $resp
    $json = $resp | ConvertFrom-Json
    Write-Host "status=$($json.status) threadId=$($json.threadId)"
    Write-Host "executedNodes=$($json.executedNodes -join ' → ')"
    Write-Host "riskLevel=$($json.state.riskLevel) reply=$($json.reply)"
}

if ($State) {
    $resp = Get-Json "$base/api/projects/$projectId/after-sale/$ThreadId"
    Write-Utf8 (Join-Path $outDir "$tag-state.json") $resp
    $json = $resp | ConvertFrom-Json
    Write-Host "检查点 $($json.checkpointCount) 条："
    $json.checkpoints | ForEach-Object { Write-Host "  seq=$($_.checkpointId) node=$($_.nodeId) next=$($_.nextNodeId) at=$($_.savedAt)" }
}

if ($Resume) {
    $resp = Post-Json "$base/api/projects/$projectId/after-sale/$ThreadId/resume" @{ decision = $Decision; sessionId = 'graph-s1' }
    Write-Utf8 (Join-Path $outDir "$tag-resume.json") $resp
    $json = $resp | ConvertFrom-Json
    Write-Host "status=$($json.status)"
    Write-Host "executedNodes=$($json.executedNodes -join ' → ')"
    Write-Host "reply=$($json.reply)"
}
