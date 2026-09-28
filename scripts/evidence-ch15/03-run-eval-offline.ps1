# 第 15 掌验收脚本 3/5：L4 离线回归评测——把真实输出快照重放一遍。
# 判据：重放结论与录下来的基线一致；不一致就说明判据或数据集被改动过。
# 它不调模型、几秒跑完，适合放进 nightly。
# 用法：pwsh -File scripts/evidence-ch15/03-run-eval-offline.ps1
# 注意：不要用 Stop——mvnw 往 stderr 写警告时，PS 5.1 会把它当成终止性错误，脚本会在打印结论前中断。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '03-eval-offline-replay.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'

Push-Location $repo
try {
    & .\mvnw -B -ntp test -pl digital-human "-Dsurefire.groups=eval" "-Dsurefire.excludedGroups=" 2>&1 |
        Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Select-String -Path $out -Pattern 'L4|基线|用例覆盖|重放|Tests run:' | ForEach-Object { $_.Line.Trim() }
}
finally {
    Pop-Location
}
