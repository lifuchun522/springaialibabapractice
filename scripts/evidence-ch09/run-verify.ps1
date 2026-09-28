# 第 9 掌真实验收脚本（PowerShell，UTF-8 with BOM）
# 用真实 DeepSeek + MySQL + MCP Server 跑一遍 Agent 主链路，落证据到 evidence-ch09/
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

# ---- 1. 注册 / 登录 ----
$user = 'agent-verify-' + (Get-Date -Format 'HHmmss')
$null = Post-Json "$base/api/auth/register" @{ username = $user; password = 'password123' }
$login = Post-Json "$base/api/auth/login" @{ username = $user; password = 'password123' } | ConvertFrom-Json
$token = $login.token
Write-Host "[OK] 登录成功 userId=$($login.userId)"

# ---- 2. 建项目 ----
$project = Post-Json "$base/api/projects" @{
    name         = 'agent-verify'
    title        = 'Agent 真实验收'
    themeColor   = '#2F6BFF'
    openingLine  = '你好，我是小九。'
    closingLine  = '再见。'
    status       = 'ACTIVE'
    systemPrompt = '你是数字人小九，回答简短口语化。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
$projectId = $project.id
Write-Host "[OK] 项目 id=$projectId"

# ---- 3. 灌一份知识，用来验证 Agent 会自己决定检索 ----
$doc = Post-Json "$base/api/projects/$projectId/documents" @{
    docName = '深圳展厅参观手册.md'
    content = '深圳展厅开放时间是每天九点到十八点，周一闭馆。参观免费，无需门票。团体讲解需要提前三天预约，预约电话 0755-12345678。展厅共有二十个停车位，先到先得。'
} @{ 'X-Token' = $token } | ConvertFrom-Json
Write-Host "[OK] 知识入库 chunks=$($doc.chunks)"

function Ask($question, $sessionId, $name) {
    $r = Post-Json "$base/api/projects/$projectId/agent/chat" @{ question = $question; sessionId = $sessionId }
    Write-Utf8 (Join-Path $outDir "$tag-$name.json") $r
    Write-Host "--- $name ---"
    Write-Host $r
    return ($r | ConvertFrom-Json)
}

# ---- 验收 1：事件序列完整可看（Agent 自己决定调工具/检索） ----
$r1 = Ask '深圳展厅的开放时间是几点？周一开放吗？' 'verify-s1' 'acc1-event-sequence'

# ---- 验收 2：账本 + 会话统计工具（Agent 路径也要落账） ----
$r2 = Ask '这个会话现在共有多少条消息？用户问了几次？' 'verify-s1' 'acc2-session-stats'

# ---- 验收 3：远程 MCP 工具（真远程调用） ----
$r3 = Ask '帮我查一下深圳展厅 2026-10-03 下午的场次还有多少余位。' 'verify-s3' 'acc3-remote-tool'

# ---- 验收 4：对外仍然是一个字符串 ----
$r4 = Ask '用一句话介绍你自己。' 'verify-s4' 'acc4-single-string'

Write-Host "PROJECT_ID=$projectId"
Write-Host "TOKEN=$token"
Write-Host "TRACE1=$($r1.traceId) TRACE2=$($r2.traceId) TRACE3=$($r3.traceId) TRACE4=$($r4.traceId)"
