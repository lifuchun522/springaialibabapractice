# 第 16 掌证据 2/4：真实启动一次，抓健康分组与 SSE 心跳。
# 前置：MySQL（本仓库用 dh-mysql:33079）与 MCP Server（dh-quick-mcp:8081）可用；DEEPSEEK_API_KEY 可从机器级环境变量读取。
# 说明：为了让心跳在几十秒内可见，这里把间隔临时改成 2s；生产 profile 里是 15s。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
$env:HTTPS_PROXY = ''; $env:HTTP_PROXY = ''; $env:all_proxy = ''
if (-not $env:DEEPSEEK_API_KEY) { $env:DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine') }
$env:DIGITAL_HUMAN_DB_URL = 'jdbc:mysql://127.0.0.1:33079/digital_human?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DIGITAL_HUMAN_DB_USER = 'root'
$env:DIGITAL_HUMAN_DB_PASSWORD = 'root'
$env:MCP_SERVER_URL = 'http://localhost:8081'

$jar = Join-Path $repo 'digital-human\target\digital-human-0.0.1-SNAPSHOT.jar'
$log = Join-Path $PSScriptRoot '02-app-run.log'
$proc = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList @('-jar', $jar, '--server.port=8090', '--digital-human.sse.heartbeat=2s') `
    -RedirectStandardOutput $log -RedirectStandardError "$log.err" -PassThru

Write-Output "等待启动（最多 60s）…"
for ($i = 0; $i -lt 60; $i++) {
    Start-Sleep -Seconds 1
    if (Select-String -Path $log -Pattern 'Started DigitalHumanApplication' -Quiet) { break }
}
Select-String -Path $log -Pattern 'Started DigitalHumanApplication|MCP 远程工具已发现' | ForEach-Object { $_.Line }

curl.exe -s -o (Join-Path $PSScriptRoot '03-health.json')          http://127.0.0.1:8090/actuator/health
curl.exe -s -o (Join-Path $PSScriptRoot '03-health-liveness.json')  http://127.0.0.1:8090/actuator/health/liveness
curl.exe -s -o (Join-Path $PSScriptRoot '03-health-readiness.json') http://127.0.0.1:8090/actuator/health/readiness
Write-Output "readiness: $(Get-Content (Join-Path $PSScriptRoot '03-health-readiness.json') -Raw -Encoding UTF8)"

$q = [System.Uri]::EscapeDataString('请逐段写一篇不少于 300 字的自我介绍')
curl.exe -N -s --trace-time --trace-ascii (Join-Path $PSScriptRoot '04-sse-heartbeat-trace.txt') `
    "http://127.0.0.1:8090/api/projects/1/chat/stream?text=$q&sessionId=ch16-evidence" |
    Out-File (Join-Path $PSScriptRoot '04-sse-raw.txt') -Encoding UTF8

Stop-Process -Id $proc.Id -Force
Write-Output "已停止进程 PID=$($proc.Id)"
