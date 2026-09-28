# 第 15 掌验收脚本 4/5：L5 在线评测——真实模型跑六类回归集，落可追溯记录。
# 前置：DEEPSEEK_API_KEY（脚本会先读进程环境变量，再读机器级变量）。
# 判据：记录文件里带数据集版本、模型标识、运行时间、逐条明细；每条 LIVE 用例都有真实回答。
# 用法：pwsh -File scripts/evidence-ch15/04-run-eval-live.ps1
# 注意：不要用 Stop——mvnw 往 stderr 写警告时，PS 5.1 会把它当成终止性错误，脚本会在打印结论前中断。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '04-eval-live.txt'
$env:JAVA_HOME = 'D:\tool\java\jdk-21'
$env:HTTPS_PROXY = ''
$env:HTTP_PROXY = ''

if (-not $env:DEEPSEEK_API_KEY) {
    $env:DEEPSEEK_API_KEY = [Environment]::GetEnvironmentVariable('DEEPSEEK_API_KEY', 'Machine')
}
if (-not $env:DEEPSEEK_API_KEY) {
    throw '缺少 DEEPSEEK_API_KEY：在线评测必须走真实模型，没有 Key 就不要假装跑过。'
}
Write-Output "key length = $($env:DEEPSEEK_API_KEY.Length)"

Push-Location $repo
try {
    & .\mvnw -B -ntp test -pl digital-human "-Dtest=LiveEvalRunTest" "-Dsurefire.excludedGroups=" 2>&1 |
        Tee-Object -FilePath $out | Out-Null
    Write-Output "exit=$LASTEXITCODE  evidence=$out"

    $runDir = Join-Path $repo 'digital-human\target\eval-runs'
    $record = Get-ChildItem $runDir -Filter 'eval-*.json' | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    Copy-Item $record.FullName (Join-Path $PSScriptRoot '04-eval-run-record.json') -Force
    Copy-Item (Join-Path $runDir 'recorded-answers.json') (Join-Path $PSScriptRoot '04-recorded-baseline.json') -Force
    Write-Output "record=$($record.Name)"
    Select-String -Path $out -Pattern '回归集|^(qa|pk|tc|wf|ma|sr)-|\[FAIL\]|\[校准不一致\]|Judge' |
        ForEach-Object { $_.Line }
}
finally {
    Pop-Location
}
