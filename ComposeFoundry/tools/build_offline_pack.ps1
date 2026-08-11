# ComposeFoundry 离线包构建脚本（Windows / PowerShell）
# 生成真实 Compose 重型渲染离线包（B 类）——Windows 版
#
# 用法:
#   .\tools\build_offline_pack.ps1 <sdkId> <version> <entryClass> [<maven坐标>...]
#
# 示例（轻量版——仅插件代码）:
#   .\tools\build_offline_pack.ps1 com.foundry.plugin.composedsl 1.0.0 com.foundry.plugin.composedsl.ComposeDslRenderEngine
#
# 示例（完整 Compose 重型包）:
#   .\tools\build_offline_pack.ps1 com.foundry.sdk.compose_runtime 1.6.0 com.foundry.plugin.composedsl.ComposeDslRenderEngine androidx.compose.runtime:compose-runtime:1.6.0 androidx.compose.ui:ui:1.6.0

param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$SdkId,
    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Version,
    [Parameter(Mandatory = $true, Position = 2)]
    [string]$EntryClass,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$MavenCoords
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path "$scriptRoot\.."

# ═══════════════════════════════════════════════════════════
# 1. Android SDK & d8
# ═══════════════════════════════════════════════════════════

$sdkRoot = if ($env:ANDROID_HOME) { $env:ANDROID_HOME }
           elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT }
           else { Join-Path $env:LOCALAPPDATA "Android\Sdk" }

if (-not (Test-Path $sdkRoot)) {
    Write-Error "Android SDK not found. Set ANDROID_HOME."
    exit 1
}

$btDir = Join-Path $sdkRoot "build-tools"
$btVer = Get-ChildItem $btDir -Directory | Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $btVer) {
    Write-Error "build-tools not installed."
    exit 1
}
$d8 = Join-Path $btVer.FullName "d8.bat"
if (-not (Test-Path $d8)) {
    Write-Error "d8 not found: $d8"
    exit 1
}
Write-Host "[build_offline] SDK: $sdkRoot"
Write-Host "[build_offline] d8:  $d8"

# ═══════════════════════════════════════════════════════════
# 2. Output directories
# ═══════════════════════════════════════════════════════════

$outDir = Join-Path $projectRoot "FoundrySDK\$SdkId"
$libsDir = Join-Path $outDir "libs"
Remove-Item -Recurse -Force $outDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $libsDir | Out-Null

$dexFiles = [System.Collections.ArrayList]::new()
$androidJar = Join-Path $sdkRoot "platforms\android-34\android.jar"

function Invoke-D8 {
    param([string]$DexName, [string[]]$Jars, [string]$Tag)
    $cmdArgs = @($d8, "--release", "--output", "$libsDir\$DexName")
    if (Test-Path $androidJar) {
        $cmdArgs += "--lib"; $cmdArgs += $androidJar
    }
    $cmdArgs += $Jars
    Write-Host "  d8: $Tag -> $DexName"
    & cmd /c $cmdArgs 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        Write-Error "d8 failed: $DexName"
        exit 1
    }
    [void]$dexFiles.Add("libs/$DexName")
    Write-Host "    OK: $DexName ($((Get-Item "$libsDir\$DexName").Length) bytes)"
}

# ═══════════════════════════════════════════════════════════
# 3. Plugin compose-dsl classes
# ═══════════════════════════════════════════════════════════

$pluginJar = Join-Path $projectRoot "plugins\plugin-compose-dsl\build\intermediates\aar_main_jar\debug\classes.jar"
if (Test-Path $pluginJar) {
    Write-Host "[build_offline] Packing plugin-compose-dsl..."
    Invoke-D8 -DexName "plugin-composedsl.dex" -Jars @($pluginJar) -Tag "plugin-compose-dsl"
} else {
    Write-Warning "plugin-compose-dsl classes.jar not found. Run: gradlew :plugins:plugin-compose-dsl:assembleDebug"
}

# ═══════════════════════════════════════════════════════════
# 4. Maven AAR download -> extract classes.jar -> DEX
# ═══════════════════════════════════════════════════════════

$tempRoot = Join-Path $env:TEMP "foundry_build_$(Get-Random)"
New-Item -ItemType Directory -Force -Path $tempRoot | Out-Null

try {
    Write-Host "[build_offline] Downloading Maven deps..."
    foreach ($coord in $MavenCoords) {
        $parts = $coord -split ':'
        if ($parts.Count -lt 3) {
            Write-Warning "  skipping invalid coord: $coord"
            continue
        }
        $group = $parts[0]
        $artifact = $parts[1]
        $ver = $parts[2]
        $mavenPath = ($group -replace '\.', '/') + "/$artifact/$ver/$artifact-$ver.aar"
        $url = "https://repo1.maven.org/maven2/$mavenPath"
        $tmpDir = Join-Path $tempRoot $artifact
        New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null
        $aarFile = Join-Path $tmpDir "a.aar"

        Write-Host "  downloading: $url"
        try {
            Invoke-WebRequest -Uri $url -OutFile $aarFile -ErrorAction Stop
        } catch {
            Write-Warning "  download failed: $url"
            continue
        }

        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $zip = [System.IO.Compression.ZipFile]::OpenRead($aarFile)
        $entry = $zip.Entries | Where-Object { $_.FullName -eq "classes.jar" } | Select-Object -First 1
        if ($entry) {
            $classesTmp = Join-Path $tmpDir "classes.jar"
            [System.IO.Compression.ZipFileExtensions]::ExtractToFile($entry, $classesTmp, $true)
            $zip.Dispose()
            Invoke-D8 -DexName "$artifact.dex" -Jars @($classesTmp) -Tag "$group:$artifact:$ver"
        } else {
            $zip.Dispose()
            Write-Warning "  no classes.jar in AAR, skipping"
        }
    }
} finally {
    Remove-Item -Recurse -Force $tempRoot -ErrorAction SilentlyContinue
}

# ═══════════════════════════════════════════════════════════
# 5. Generate foundry-pack.json
# ═══════════════════════════════════════════════════════════

$libsLines = ($dexFiles | ForEach-Object { "    `"$($_)`"" }) -join ",`n"
$manifestJson = "{`n  `"id`": `"$SdkId`",`n  `"version`": `"$Version`",`n  `"type`": `"VIEW_RENDERER`",`n  `"entryClass`": `"$EntryClass`",`n  `"libraries`": [`n$libsLines`n  ],`n  `"minCoreVersion`": `"0.2.0`",`n  `"minSdk`": 26`n}"
$manifestPath = Join-Path $outDir "foundry-pack.json"
Set-Content -Path $manifestPath -Value $manifestJson -Encoding UTF8
Write-Host "  manifest: $manifestPath"

# ═══════════════════════════════════════════════════════════
# 6. Summary
# ═══════════════════════════════════════════════════════════

$totalSize = (Get-ChildItem $libsDir | Measure-Object Length -Sum).Sum
Write-Host ""
Write-Host "========================================"
Write-Host " Pack ready: $outDir"
Write-Host " Libraries: $($dexFiles.Count) dex files, $totalSize bytes"
Write-Host ""
Write-Host " Deploy:"
Write-Host "   adb push '$outDir' /sdcard/Android/data/com.foundry.preview/files/FoundrySDK/"
Write-Host "   (or run: .\tools\deploy_plugins.ps1)"
Write-Host "========================================"
