# 第 9 掌验收 3：工具失败后重试、重试耗尽后仍能回答（真实远程工具断连）
# 用法：先起好 MCP Server（8082）与指向它的应用，跑一次「正常调用」；再杀掉 MCP Server，跑「故障调用」。
param(
    [string]$base = 'http://127.0.0.1:8080',
    [int]$projectId = 4,
    [string]$tag = 'acc3',
    [string]$sessionId = 'verify-s5'
)
$ErrorActionPreference = 'Stop'
$outDir = $PSScriptRoot

function Write-Utf8($path, $text) {
    [System.IO.File]::WriteAllText($path, $text, (New-Object System.Text.UTF8Encoding($false)))
}

$question = '帮我查一下深圳展厅 2026-10-03 下午的场次还有多少余位。'
$file = Join-Path $env:TEMP ('req-' + [guid]::NewGuid().ToString('N') + '.json')
Write-Utf8 $file (@{ question = $question; sessionId = $sessionId } | ConvertTo-Json)
$resp = & curl.exe -s -X POST "$base/api/projects/$projectId/agent/chat" `
    -H 'Content-Type: application/json; charset=utf-8' --noproxy '*' -d "@$file"
Remove-Item $file -Force

Write-Utf8 (Join-Path $outDir "$tag-result.json") $resp
Write-Host "--- $tag ---"
Write-Host $resp
$json = $resp | ConvertFrom-Json
Write-Host "TRACE=$($json.traceId)"
