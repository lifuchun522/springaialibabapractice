# 第 16 掌证据 1/4：配置缺失必须在启动期失败（而不是第一次请求的 401）。
# 判据：进程起不来，且错误信息点名环境变量与声明位置。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '01-startup-fail-fast.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
$env:DEEPSEEK_API_KEY = ''      # 故意清空：这一步验的就是「缺失时的表现」
$env:HTTPS_PROXY = ''; $env:HTTP_PROXY = ''; $env:all_proxy = ''

$jar = Join-Path $repo 'digital-human\target\digital-human-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw "先打包：./mvnw -B -ntp package -DskipTests -pl digital-human -am" }

& "$env:JAVA_HOME\bin\java.exe" -jar $jar --server.port=8091 2>&1 |
    Select-String -Pattern '部署契约|IllegalStateException|Application run failed|Started' |
    ForEach-Object { $_.Line } | Out-File $out -Encoding UTF8
Write-Output "evidence=$out"
Get-Content $out -Encoding UTF8 | Select-Object -First 3
