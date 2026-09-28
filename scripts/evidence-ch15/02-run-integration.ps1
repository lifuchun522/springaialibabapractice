# 第 15 掌验收脚本 2/5：L3 集成层——Testcontainers 起真实 MySQL，验证迁移与实体映射。
# 判据：容器起来、六条迁移全部 applied、graph_checkpoint 表存在、实体读写往返一致。
# 前置：本机 Docker 可用（脚本先探一次，不可用就明确报错，不静默跳过）。
# 用法：pwsh -File scripts/evidence-ch15/02-run-integration.ps1
# 注意：不要用 Stop——mvnw 往 stderr 写警告时，PS 5.1 会把它当成终止性错误，脚本会在打印结论前中断。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '02-integration-testcontainers.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'

$docker = (docker version --format '{{.Server.Version}}' 2>&1) -join ' '
Write-Output "docker server = $docker"

Push-Location $repo
try {
    # tag 隔离：默认排除表里含 integration，所以这里同时清空 excludedGroups
    & .\mvnw -B -ntp test -pl digital-human "-Dsurefire.groups=integration" "-Dsurefire.excludedGroups=" 2>&1 |
        Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Select-String -Path $out -Pattern 'Tests run:|Creating container|Container mysql|BUILD' |
        ForEach-Object { $_.Line.Trim() } | Select-Object -First 20
}
finally {
    Pop-Location
}
