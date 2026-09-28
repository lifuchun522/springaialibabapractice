# 环境自检：照第 2 掌的基线逐项核对，任何一项不过就先去修环境，别急着写代码。
# 用法：powershell -NoProfile -ExecutionPolicy Bypass -File scripts\env-check.ps1
# 说明：这里刻意不用 $ErrorActionPreference='Stop'——java -version 把版本号写在 stderr，
#       Stop 模式会把它当成终止错误，让自检脚本自己挂掉。
$failed = 0

function Check($name, $ok, $detail) {
    if ($ok) { Write-Host "[ OK ] $name  $detail" -ForegroundColor Green }
    else { Write-Host "[FAIL] $name  $detail" -ForegroundColor Red; $script:failed++ }
}

Write-Host '--- 工具链 ---'
$javaVersion = ((& java -version 2>&1 | Out-String) -split "`n")[0]
Check 'JDK 21' ($javaVersion -match '"21\.') $javaVersion.Trim()
Check 'Git' (Get-Command git -ErrorAction SilentlyContinue) ((& git --version 2>&1 | Out-String).Trim())
Check 'Maven Wrapper' (Test-Path 'mvnw.cmd') 'mvnw / mvnw.cmd 存在（版本由仓库锁定）'
Check 'curl' (Get-Command curl.exe -ErrorAction SilentlyContinue) (((& curl.exe --version 2>&1 | Out-String) -split "`n")[0].Trim())

Write-Host '--- 模型通道 ---'
$key = $env:DEEPSEEK_API_KEY
if ($key) {
    Check 'DEEPSEEK_API_KEY' ($key.Length -gt 10) "已注入（长度 $($key.Length)，值不打印）"
} else {
    Check 'DEEPSEEK_API_KEY' $false '未注入：服务会在启动期 fail-fast，请先设置环境变量'
}

Write-Host '--- 构建与测试 ---'
if ($failed -eq 0) {
    & .\mvnw.cmd -B -ntp clean test
    Check 'mvnw clean test' ($LASTEXITCODE -eq 0) "退出码 $LASTEXITCODE"
} else {
    Write-Host '前置项未通过，跳过构建（先修环境，再谈代码）' -ForegroundColor Yellow
}

if ($failed -gt 0) {
    Write-Host "`n自检失败 $failed 项" -ForegroundColor Red
    exit 1
}
Write-Host "`n自检全部通过" -ForegroundColor Green
