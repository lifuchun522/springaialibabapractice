# 第 14 掌：源码定位脚本（PowerShell，UTF-8 with BOM）
#
# 为什么要有它：源码阅读的结论如果只写在文档里，就会随分支漂移（main 变一次，结论就过期一次）。
# 这个脚本把「事实源」钉在两件事上：
#   1) 依赖坐标 + 版本（不是「我打开的那份源码」）；
#   2) 从 Maven Central 下的 *-sources.jar（发布出去的字节码对应的源码），不是 main。
# 于是任何人跑一遍就能复算：模块 → 文件 → 行号。
#
# 用法：
#   .\locate-source.ps1 ToolRetryInterceptor          # 列出符号所在文件与行号
#   .\locate-source.ps1 -Symbol "interceptToolCall"   # 只搜方法/字段名
#   .\locate-source.ps1 -Symbol "retryOn" -Context 3  # 带上下文
param(
    [Parameter(Mandatory = $true)][string]$Symbol,
    [int]$Context = 0,
    [string]$Cache = ''
)

$ErrorActionPreference = 'Stop'

# 默认缓存目录：仓库根下的 .source-cache（ 在 param 默认值里取不到，所以放这里算）
if ([string]::IsNullOrEmpty($Cache)) {
    $Cache = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\.source-cache'))
}

# 锁定版本：与 pom.xml 里 spring-ai-alibaba-bom / spring-ai-bom 的版本一致。
# 换版本一定要同步改这里，否则脚本给出的行号就不是你跑的那份字节码。
$coordinates = @(
    @{ module = 'spring-ai-alibaba-agent-framework'; version = '1.1.2.2'; path = 'com/alibaba/cloud/ai/spring-ai-alibaba-agent-framework/1.1.2.2/spring-ai-alibaba-agent-framework-1.1.2.2-sources.jar' },
    @{ module = 'spring-ai-alibaba-graph-core'; version = '1.1.2.2'; path = 'com/alibaba/cloud/ai/spring-ai-alibaba-graph-core/1.1.2.2/spring-ai-alibaba-graph-core-1.1.2.2-sources.jar' },
    @{ module = 'spring-ai-model'; version = '1.1.2'; path = 'org/springframework/ai/spring-ai-model/1.1.2/spring-ai-model-1.1.2-sources.jar' },
    @{ module = 'spring-ai-client-chat'; version = '1.1.2'; path = 'org/springframework/ai/spring-ai-client-chat/1.1.2/spring-ai-client-chat-1.1.2-sources.jar' }
)

Add-Type -AssemblyName System.IO.Compression.FileSystem

foreach ($coordinate in $coordinates) {
    $srcDir = Join-Path $Cache ('src\' + $coordinate.module)
    if (-not (Test-Path $srcDir)) {
        $jarPath = Join-Path $Cache (Split-Path $coordinate.path -Leaf)
        if (-not (Test-Path $jarPath)) {
            Write-Output "[下载] $($coordinate.module)-$($coordinate.version)-sources.jar"
            & curl.exe -s -L -o $jarPath "https://repo1.maven.org/maven2/$($coordinate.path)"
        }
        New-Item -ItemType Directory -Force -Path $srcDir | Out-Null
        $zip = [System.IO.Compression.ZipFile]::OpenRead($jarPath)
        foreach ($entry in $zip.Entries) {
            if ($entry.Name -eq '') { continue }
            $target = Join-Path $srcDir $entry.FullName
            New-Item -ItemType Directory -Force -Path (Split-Path $target) | Out-Null
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $target, $true)
        }
        $zip.Dispose()
    }

    $files = Get-ChildItem $srcDir -Recurse -Filter '*.java'
    foreach ($file in $files) {
        $lines = Get-Content $file.FullName -Encoding UTF8
        for ($i = 0; $i -lt $lines.Count; $i++) {
            if ($lines[$i] -match [regex]::Escape($Symbol)) {
                $relative = $file.FullName.Substring($srcDir.Length + 1) -replace '\\', '/'
                Write-Output ("{0}@{1}  {2}:{3}" -f $coordinate.module, $coordinate.version, $relative, ($i + 1))
                Write-Output ("      {0}" -f $lines[$i].Trim())
                if ($Context -gt 0) {
                    for ($c = [Math]::Max(0, $i - $Context); $c -le [Math]::Min($lines.Count - 1, $i + $Context); $c++) {
                        Write-Output ("      | {0}" -f $lines[$c].Trim())
                    }
                }
            }
        }
    }
}
