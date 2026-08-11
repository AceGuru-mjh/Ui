# ComposeFoundry 部署脚本（Windows / PowerShell）
# 用法: .\tools\deploy_plugins.ps1 [-Plugin compose|json] [-PushOnly] [-Rebuild]

param(
    [ValidateSet("all", "compose", "json")]
    [string]$Plugin = "all",
    [switch]$PushOnly,
    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path "$scriptRoot\.."
$appPackage = "com.foundry.preview"

# ---- tool check ----
function Test-Command($cmd) { return [bool](Get-Command $cmd -ErrorAction SilentlyContinue) }

if (-not (Test-Command adb)) {
    Write-Error "adb not found. Install Android SDK Platform-Tools."
    exit 1
}

$devices = adb devices 2>&1 | Select-String -Pattern "device$" | ForEach-Object { ($_ -split "\s+")[0] }
if (-not $devices) {
    Write-Error "No device connected."
    exit 1
}
Write-Host "[deploy] Connected: $devices" -ForegroundColor Green

# ---- collect packs ----
$pluginModules = @(
    @{ Name = "compose"; Module = "plugins\plugin-compose-dsl" },
    @{ Name = "json";    Module = "plugins\plugin-simple-json" }
)

$packs = New-Object System.Collections.ArrayList
foreach ($mod in $pluginModules) {
    if ($Plugin -ne "all" -and $Plugin -ne $mod.Name) { continue }
    $packDir = Join-Path $projectRoot ($mod.Module + "\build\foundry-pack")
    if (-not (Test-Path $packDir)) {
        Write-Host "[deploy] $($mod.Name): build not found" -ForegroundColor Yellow
        continue
    }
    $manifestFile = Join-Path $packDir "foundry-pack.json"
    if (-not (Test-Path $manifestFile)) {
        Write-Warning "[deploy] $($mod.Name): missing foundry-pack.json"
        continue
    }
    [void]$packs.Add(@{ Mod = $mod; PackDir = $packDir; ManifestFile = $manifestFile })
}

if ($packs.Count -eq 0) {
    Write-Warning "[deploy] No packs ready to deploy."
    exit 0
}

# ---- build if needed ----
if (-not $PushOnly) {
    $built = @()
    foreach ($p in $packs) {
        if ($Rebuild -or -not (Test-Path $p.PackDir)) {
            $module = $p.Mod.Module
            if ($module -notin $built) { $built += $module }
        }
    }
    foreach ($module in $built) {
        $taskPath = ":$($module -replace '\\', ':'):packagePlugin"
        Write-Host "[deploy] gradlew $taskPath"
        $gwJar = Join-Path $projectRoot "gradle\wrapper\gradle-wrapper.jar"
        & java -cp $gwJar org.gradle.wrapper.GradleWrapperMain -p $projectRoot $taskPath 2>&1 | ForEach-Object {
            if ($_ -match "BUILD (SUCCESS|FAILED)") { Write-Host $_ }
            elseif ($_ -match "packaged|FAILURE") {
                $fg = if ($_ -match "FAIL") { "Red" } else { "Green" }
                Write-Host $_ -ForegroundColor $fg
            }
        }
        if ($LASTEXITCODE -ne 0) { Write-Error "Build failed: $module"; exit 1 }
    }
}

# ---- push to device ----
$targetRoot = "/sdcard/Android/data/$appPackage/files/FoundrySDK"
$totalPushed = 0
$totalBytes = 0

foreach ($p in $packs) {
    $json = Get-Content $p.ManifestFile -Raw -Encoding UTF8 | ConvertFrom-Json
    $packId = $json.id
    $devicePackDir = "$targetRoot/$packId"

    Write-Host ""
    Write-Host "[deploy] $($p.Mod.Name) -> $packId" -ForegroundColor Cyan
    Write-Host "  source: $($p.PackDir)"
    Write-Host "  target: $devicePackDir"

    adb shell "mkdir -p $devicePackDir" 2>$null | Out-Null

    $files = Get-ChildItem $p.PackDir -File
    $baseBytes = 0
    foreach ($file in $files) {
        $dest = "$devicePackDir/$($file.Name)"
        Write-Host "  push: $($file.Name) ($($file.Length) bytes)"
        adb push $file.FullName $dest 2>$null | Out-Null
        $baseBytes += $file.Length
    }

    $verify = adb shell "unzip -l $devicePackDir/classes.jar 2>/dev/null | grep classes.dex" 2>$null
    if ($verify) {
        Write-Host "  [OK] classes.dex verified" -ForegroundColor Green
    }

    $totalPushed++
    $totalBytes += $baseBytes
    Write-Host "  done: $($files.Count) files, $baseBytes bytes" -ForegroundColor Green
}

# ---- summary ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host " Deployed: $totalPushed packs, $totalBytes bytes" -ForegroundColor Green
Write-Host " Device path: $targetRoot/"
Write-Host ""
Write-Host " Next:"
Write-Host "  1. Trigger preview in app -> :renderer scans SDKs"
Write-Host "  2. Logs: adb logcat -s LocalSdkScanner RenderCaptureActivity RendererService"
Write-Host "  3. Refresh: call LocalSdkScanner.refresh() or restart app"
Write-Host "========================================" -ForegroundColor Green
