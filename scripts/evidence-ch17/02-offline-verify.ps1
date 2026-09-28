# 第 17 掌证据 2/2：离线门禁（身份传播 / 失败分类 / 调用树 / 契约 / 诊断接口）。
# 先跑这一步：它会 clean target/，此时若有进程占用 jar，clean 会失败。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '11-offline-verify.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
Push-Location $repo
try {
    & .\mvnw -B -ntp clean verify 2>&1 | Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Select-String -Path $out -Pattern 'Tests run:.*Failures' | ForEach-Object { $_.Line.Trim() }
}
finally { Pop-Location }
