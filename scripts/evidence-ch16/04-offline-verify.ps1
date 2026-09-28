# 第 16 掌证据 4/4：离线分层门禁（ArchUnit 依赖方向 + 启动契约 + SSE 心跳 + 接口契约）。
# 先跑这一步：它会 clean target/，若此时有进程占用 jar，clean 会失败（本掌实测踩到一次）。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '07-offline-verify.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
Push-Location $repo
try {
    & .\mvnw -B -ntp clean verify 2>&1 | Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Select-String -Path $out -Pattern 'Tests run:.*Failures' | ForEach-Object { $_.Line.Trim() }
}
finally { Pop-Location }
