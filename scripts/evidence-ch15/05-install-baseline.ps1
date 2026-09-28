# 第 15 掌验收脚本 5/5：把 L5 录到的答案快照装进 L4 的基线。
# 为什么要单独一步：快照是**证据**，进版本库要有人看过一眼——
# 让评测任务直接写进 src/test/resources，等于让一次运行自动改基线，
# 那样「基线没被动过」这句话就不值钱了。
# 用法：pwsh -File scripts/evidence-ch15/05-install-baseline.ps1
# 注意：不要用 Stop——mvnw 往 stderr 写警告时，PS 5.1 会把它当成终止性错误，脚本会在打印结论前中断。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$source = Join-Path $repo 'digital-human\target\eval-runs\recorded-answers.json'
$targetDir = Join-Path $repo 'digital-human\src\test\resources\regression\recorded'
$target = Join-Path $targetDir 'answers.json'

if (-not (Test-Path $source)) {
    throw "没有找到快照：$source（先跑 04-run-eval-live.ps1）"
}
New-Item -ItemType Directory -Force -Path $targetDir | Out-Null
Copy-Item $source $target -Force

$baseline = Get-Content $target -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Output "已装入基线：$target"
Write-Output "数据集版本 : $($baseline.datasetVersion)"
Write-Output "模型       : $($baseline.model)"
Write-Output "录制时间   : $($baseline.recordedAt)"
Write-Output "快照条数   : $($baseline.entries.Count)"
Write-Output "通过基线   : $($baseline.passIds -join ', ')"
Write-Output "其中失败   : $((($baseline.entries | Where-Object { $baseline.passIds -notcontains $_.id }) | ForEach-Object { $_.id }) -join ', ')"
