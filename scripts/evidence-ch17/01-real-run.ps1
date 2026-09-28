# 第 17 掌证据 1/2：真实启动一次，跑三类请求并按 traceId 取回调用树。
# 前置：MySQL（dh-mysql:33079）、MCP Server（dh-quick-mcp:8081）、DEEPSEEK_API_KEY。
# 说明：Windows 上 curl 传中文 JSON 必须用文件（--data-binary @file），内联会把引号弄坏。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
$env:HTTPS_PROXY = ''; $env:HTTP_PROXY = ''; $env:all_proxy = ''
if (-not $env:DEEPSEEK_API_KEY) { $env:DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine') }
$env:DIGITAL_HUMAN_DB_URL = 'jdbc:mysql://127.0.0.1:33079/digital_human?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DIGITAL_HUMAN_DB_USER = 'root'
$env:DIGITAL_HUMAN_DB_PASSWORD = 'root'
$env:MCP_SERVER_URL = 'http://localhost:8081'
$base = 'http://127.0.0.1:8090'
$enc = New-Object System.Text.UTF8Encoding($false)

$jar = Join-Path $repo 'digital-human\target\digital-human-0.0.1-SNAPSHOT.jar'
$log = Join-Path $PSScriptRoot '01-app-run.log'
$proc = Start-Process -FilePath "$env:JAVA_HOME\bin\java.exe" `
    -ArgumentList @('-jar', $jar, '--server.port=8090') `
    -RedirectStandardOutput $log -RedirectStandardError (Join-Path $PSScriptRoot '01-app-run.err') -PassThru
for ($i = 0; $i -lt 60; $i++) { Start-Sleep -Seconds 1; if (Select-String -Path $log -Pattern 'Started DigitalHumanApplication' -Quiet) { break } }
Select-String -Path $log -Pattern 'Started DigitalHumanApplication' | ForEach-Object { $_.Line }

# 登录一个观测账号（诊断接口要令牌）
$user = "obs$([int](Get-Date -Format HHmmss))"
$regJson = Join-Path $env:TEMP 'ch17-reg.json'
[System.IO.File]::WriteAllText($regJson, "{`"username`":`"$user`",`"password`":`"pass-123456`"}", $enc)
curl.exe -s -X POST "$base/api/auth/register" -H 'Content-Type: application/json' --data-binary "@$regJson" | Out-Null
$login = curl.exe -s -X POST "$base/api/auth/login" -H 'Content-Type: application/json' --data-binary "@$regJson"
$token = ([regex]::Match($login, '"token":"([^"]+)"')).Groups[1].Value

function Invoke-Scenario($name, $argsArray, $bodyFile) {
    $headerFile = Join-Path $PSScriptRoot "02-header-$name.txt"
    if ($bodyFile) {
        curl.exe -s -D $headerFile -o (Join-Path $PSScriptRoot "03-body-$name.json") -X POST $argsArray[0] -H 'Content-Type: application/json' --data-binary "@$bodyFile" | Out-Null
    } else {
        curl.exe -s -D $headerFile -o (Join-Path $PSScriptRoot "03-body-$name.json") $argsArray[0] | Out-Null
    }
    ([regex]::Match((Get-Content $headerFile -Raw), 'X-Trace-Id:\s*(\S+)')).Groups[1].Value
}

$q1 = [System.Uri]::EscapeDataString('请调用 getProjectInfo 工具，然后告诉我项目标题')
$trace1 = Invoke-Scenario 'success' @("$base/api/chat?q=$q1&projectId=1&sessionId=ch17-s1") $null

$badJson = Join-Path $env:TEMP 'ch17-bad.json'
[System.IO.File]::WriteAllText($badJson, '{"text":"你好"}', $enc)
$trace2 = Invoke-Scenario 'failure' @("$base/api/projects/999999/chat") $badJson

$ragJson = Join-Path $env:TEMP 'ch17-rag.json'
[System.IO.File]::WriteAllText($ragJson, '{"question":"我们公司在香港交易所的股票代码是多少？"}', $enc)
$trace3 = Invoke-Scenario 'rag' @("$base/api/projects/1/rag/ask") $ragJson

@{ success = $trace1; failure = $trace2; rag = $trace3 } | ConvertTo-Json | Out-File (Join-Path $PSScriptRoot '05-trace-ids.json') -Encoding UTF8
foreach ($pair in @(@('success', $trace1), @('failure', $trace2), @('rag', $trace3))) {
    $target = Join-Path $PSScriptRoot "05-tree-$($pair[0]).json"
    curl.exe -s -H "X-Token: $token" "$base/api/diagnostics/traces/$($pair[1])" | Out-File $target -Encoding UTF8
    $j = Get-Content $target -Raw -Encoding UTF8 | ConvertFrom-Json
    Write-Output ("[{0}] trace={1} spans={2} failed={3} types=[{4}]" -f $pair[0], $j.traceId, $j.spanCount, $j.failedSpans, ($j.failureTypes -join ','))
}

# 失败分类的指标是否真的注册上了（会被 Prometheus 悄悄丢弃的那种问题）
curl.exe -s "$base/actuator/prometheus" | Select-String -Pattern '^http_request_seconds_count' |
    ForEach-Object { $_.Line } | Out-File (Join-Path $PSScriptRoot '09-metric-failure-tag.txt') -Encoding UTF8
Get-Content (Join-Path $PSScriptRoot '09-metric-failure-tag.txt') -Encoding UTF8

Stop-Process -Id $proc.Id -Force
