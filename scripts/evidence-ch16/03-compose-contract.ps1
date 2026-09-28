# 第 16 掌证据 3/4：部署清单必须声明必需项（缺了就拒绝启动，而不是带病运行）。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '06-compose-required-env.txt'
$env:APP_DEEPSEEK_API_KEY = ''
Push-Location (Join-Path $repo 'deploy')
try {
    docker compose -f docker-compose.yml config 2>&1 | Out-File $out -Encoding UTF8
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
    Get-Content $out -Encoding UTF8 | Select-String -Pattern '缺少|required variable' | Select-Object -First 3 | ForEach-Object { $_.Line }
}
finally { Pop-Location }
