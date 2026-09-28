# 第 18 掌证据 1/4：构建镜像，并以 K8s 同等的安全上下文启动（只读根 + 非 root + 内存上限）。
# 前置：docker 可用；本模块已 package 出 jar；DEEPSEEK_API_KEY 可从机器级环境变量读取。
# 注意 --add-host：纯 Docker 下 host.docker.internal 不解析，忘了它应用会启动失败（实测踩到）。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
if (-not $env:DEEPSEEK_API_KEY) { $env:DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine') }
$jar = Join-Path $repo 'digital-human\target\digital-human-0.0.1-SNAPSHOT.jar'
if (-not (Test-Path $jar)) { throw '先打包：./mvnw -B -ntp package -DskipTests -pl digital-human' }

Push-Location $repo
try {
    docker build -t saa-agent:ch18 -f digital-human/Dockerfile digital-human | Select-Object -Last 2
    docker image inspect saa-agent:ch18 --format 'User={{.Config.User}} WorkDir={{.Config.WorkingDir}}' |
        Out-File (Join-Path $PSScriptRoot '01-image-inspect.txt') -Encoding UTF8

    docker rm -f ch18-agent 2>$null | Out-Null
    docker run -d --name ch18-agent --memory=1g --read-only --user 10001:10001 --tmpfs /tmp --tmpfs /app/logs `
        --add-host=host.docker.internal:host-gateway `
        -e DEEPSEEK_API_KEY=$env:DEEPSEEK_API_KEY `
        -e DIGITAL_HUMAN_DB_URL='jdbc:mysql://host.docker.internal:33079/digital_human?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' `
        -e DIGITAL_HUMAN_DB_USER=root -e DIGITAL_HUMAN_DB_PASSWORD=root `
        -e MCP_SERVER_URL='http://host.docker.internal:8081' `
        -e DIGITAL_HUMAN_MEMORY_STORE=jdbc -p 8096:8080 saa-agent:ch18 | Out-Null

    for ($i = 0; $i -lt 60; $i++) {
        Start-Sleep -Seconds 1
        if ((docker ps --filter name=ch18-agent --format '{{.Status}}') -match 'Up') { break }
    }

    $identity = Join-Path $PSScriptRoot '02-container-identity.txt'
    docker ps --filter name=ch18-agent --format '{{.Names}} {{.Status}}' | Out-File $identity -Encoding UTF8
    docker exec ch18-agent id | Add-Content $identity
    docker exec ch18-agent sh -c 'touch /app/jar-probe' 2>&1 | Add-Content $identity
    Get-Content $identity -Encoding UTF8

    # 容器感知堆：容器上限 × 75% 说明 JVM 读到了 cgroup 限制
    $heap = (curl.exe -s 'http://127.0.0.1:8096/actuator/metrics/jvm.memory.max?tag=area:heap' | ConvertFrom-Json)
    $heapMax = ($heap.measurements | Where-Object { $_.statistic -eq 'VALUE' }).value
    @"
容器内存上限          : $([math]::Round([double](docker inspect ch18-agent --format '{{.HostConfig.Memory}}')/1MB,0)) MiB
JVM 堆上限(area=heap) : $([math]::Round($heapMax/1MB,1)) MiB
MaxRAMPercentage      : 75%
"@ | Out-File (Join-Path $PSScriptRoot '04-container-aware-heap.txt') -Encoding UTF8
}
finally { Pop-Location }
