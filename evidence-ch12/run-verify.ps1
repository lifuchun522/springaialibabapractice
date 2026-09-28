# 第 12 掌真实验收（PowerShell，UTF-8 with BOM）
# 三角色协作 + 工具边界 + 记忆边界，一次跑完
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

$user = 'multi-verify-' + (Get-Date -Format 'HHmmss')
$null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
$token = (Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json).token
$project = Post-Json "$base/api/projects" @{
    name = 'multi-verify'; title = '多 Agent 验收'; themeColor = '#2F6BFF'
    openingLine = '你好。'; closingLine = '再见。'; status = 'ACTIVE'
    systemPrompt = '你是数字人小分。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
$projectId = $project.id
$null = Post-Json "$base/api/projects/$projectId/documents" @{
    docName = '项目手册.md'
    content = '深圳展厅工作日九点到十八点开放，周一闭馆，参观免费。团体讲解需提前三天预约，电话 0755-12345678。本产品支持私有化部署。'
} @{ 'X-Token' = $token }
Write-Host "[OK] 项目=$projectId"

function Ask($question, $sessionId, $tag) {
    $resp = Post-Json "$base/api/projects/$projectId/multi-agent/ask" @{ question = $question; sessionId = $sessionId }
    Write-Utf8 (Join-Path $outDir "run-$tag.json") $resp
    $json = $resp | ConvertFrom-Json
    Write-Host "--- $tag ---"
    Write-Host "route=$($json.route) hops=$($json.hops -join ' → ') memory=$($json.memorySizes | ConvertTo-Json -Compress)"
    Write-Host "reply=$($json.reply)"
    return $json
}

# 1) 接待 → 知识：一次会话内完成流转
$null = Ask '你好，我想了解一下你们展厅的开放时间' 'real-s1' 'acc1-reception-to-knowledge'
# 2) 业务：实时数据查询
$null = Ask '深圳展厅这周三下午还有余位吗？帮我查一下' 'real-s1' 'acc2-business'
# 3) 记忆边界：同一会话再问一句知识问题，看各角色记忆条数
$null = Ask '那团体讲解要提前几天预约？' 'real-s1' 'acc3-memory-isolation'
