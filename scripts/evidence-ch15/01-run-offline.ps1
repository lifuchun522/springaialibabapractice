# 第 15 掌验收脚本 1/5：离线层（L1 单元 + L2 组件）——这是 CI 的阻断路径。
# 判据：三个模块全部 BUILD SUCCESS，且测试数与 CI 一致。
# 用法：pwsh -File scripts/evidence-ch15/01-run-offline.ps1
# 注意：不要用 Stop——mvnw 往 stderr 写警告时，PS 5.1 会把它当成终止性错误，脚本会在打印结论前中断。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '01-offline-verify.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'

Push-Location $repo
try {
    & .\mvnw -B -ntp clean verify 2>&1 | Tee-Object -FilePath $out | Out-Null
    $code = $LASTEXITCODE
    Write-Output "exit=$code  evidence=$out"
    Select-String -Path $out -Pattern 'Tests run:.*Failures' | ForEach-Object { $_.Line.Trim() }
}
finally {
    Pop-Location
}
