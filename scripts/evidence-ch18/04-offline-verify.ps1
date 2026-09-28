# 第 18 掌证据 4/4：离线门禁（含记忆外置的仓储用例）。
# 先跑这一步：它会 clean target/，有进程占用 jar 时会失败。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '08-offline-verify.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
Push-Location $repo
try {
    & .\mvnw -B -ntp clean verify 2>&1 | Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Select-String -Path $out -Pattern 'Tests run:.*Failures' | ForEach-Object { $_.Line.Trim() }
}
finally { Pop-Location }
