# 第 18 掌证据 3/4：清单结构校验（不需要集群）。
# kubectl apply --dry-run=client 也要连 API Server 做 discovery，无集群时会报
# "failed to download openapi"；所以这里的判据由 scripts/validate-k8s-manifests.py 承担。
$ErrorActionPreference = 'Continue'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$out = Join-Path $PSScriptRoot '07-manifest-validation.txt'
Push-Location $repo
try {
    python scripts\validate-k8s-manifests.py 2>&1 | Tee-Object -FilePath $out
    Write-Output "exit=$LASTEXITCODE  evidence=$out"
}
finally { Pop-Location }
