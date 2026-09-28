# 第 18 掌证据 2/4：流式请求进行中发 SIGTERM，验证「滚动可无损」。
# 判据：客户端拿到完整回答（无 error 帧）；进程等请求收尾后才退出。
$ErrorActionPreference = 'Continue'
$ev = $PSScriptRoot
$container = 'ch18-agent'
if (-not (docker ps --filter "name=$container" --format '{{.Status}}')) { throw "先跑 01-real-run.ps1 起容器" }

$sseOut = Join-Path $ev '06-sse-during-sigterm.txt'
$q = [System.Uri]::EscapeDataString('请逐段写一篇不少于 400 字的自我介绍，分段输出')
$job = Start-Job -ScriptBlock {
    param($q, $out)
    & curl.exe -N -s -o $out "http://127.0.0.1:8096/api/projects/1/chat/stream?text=$q&sessionId=ch18-sse"
} -ArgumentList $q, $sseOut

Start-Sleep -Seconds 6
$started = Get-Date
docker stop -t 60 $container | Out-Null
$elapsed = [math]::Round(((Get-Date) - $started).TotalSeconds, 1)
Receive-Job $job -Wait | Out-Null
Remove-Job $job

$raw = if (Test-Path $sseOut) { Get-Content $sseOut -Raw -Encoding UTF8 } else { '' }
@"
== 终止期间的 SSE 长连接（docker stop = K8s 删除 Pod 的同等信号）==
终止时机              : 流式请求发出后 6 秒（模型仍在生成）
docker stop 耗时      : ${elapsed}s
data 帧数量           : $(([regex]::Matches($raw, '(?m)^data:')).Count)
heartbeat 帧数量      : $(([regex]::Matches($raw, ':heartbeat')).Count)
是否出现 error 帧     : $([bool]([regex]::IsMatch($raw, 'event: error')))
客户端是否拿到完整回答: $([bool]($raw.Length -gt 200 -and -not [regex]::IsMatch($raw, 'event: error')))
"@ | Out-File (Join-Path $ev '06-graceful-shutdown.txt') -Encoding UTF8
docker logs $container 2>&1 |
    Select-String -Pattern 'Commencing graceful|Graceful shutdown complete|chat/stream|HikariPool' |
    ForEach-Object { $_.Line } | Out-File (Join-Path $ev '06-graceful-shutdown.log') -Encoding UTF8
Get-Content (Join-Path $ev '06-graceful-shutdown.txt') -Encoding UTF8
