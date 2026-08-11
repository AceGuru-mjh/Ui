# ComposeFoundry 发布脚本（Windows / PowerShell）
# 用法: .\tools\release_plugins.ps1 [-Tag v0.3.0] [-DryRun] [-Draft]

param(
    [string]$Tag = "v0.3.0",
    [switch]$DryRun,
    [switch]$Draft,
    [string]$Repo = "AceGuru-mjh/Ui"
)

$ErrorActionPreference = "Stop"
$scriptRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path "$scriptRoot\.."

# ---- 1. Build all plugins ----
Write-Host "[release] Building plugins..." -ForegroundColor Cyan
$gwJar = Join-Path $projectRoot "gradle\wrapper\gradle-wrapper.jar"
$tasks = @(
    ":plugins:plugin-compose-dsl:packagePlugin",
    ":plugins:plugin-simple-json:packagePlugin",
    ":plugins:plugin-version-printer:assembleDebug"
)

foreach ($task in $tasks) {
    Write-Host "  gradlew $task"
    & java -cp $gwJar org.gradle.wrapper.GradleWrapperMain -p $projectRoot $task 2>&1 | ForEach-Object {
        if ($_ -match "BUILD (SUCCESS|FAILED)") { Write-Host $_ }
        elseif ($_ -match "packaged|FAILURE") {
            $fg = if ($_ -match "FAIL") { "Red" } else { "Green" }
            Write-Host $_ -ForegroundColor $fg
        }
    }
    if ($LASTEXITCODE -ne 0) { Write-Error "Build failed: $task"; exit 1 }
}

# ---- 2. Collect artifacts ----
$releaseDir = Join-Path $projectRoot "release"
$versionDir = Join-Path $releaseDir $Tag
Remove-Item -Recurse -Force $versionDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $versionDir | Out-Null

$packs = @(
    @{ Name = "plugin-compose-dsl"; Path = "plugins\plugin-compose-dsl\build\foundry-pack" },
    @{ Name = "plugin-simple-json"; Path = "plugins\plugin-simple-json\build\foundry-pack" }
)

$sha256Plugins = @{}

foreach ($pack in $packs) {
    $packDir = Join-Path $projectRoot $pack.Path
    if (-not (Test-Path $packDir)) {
        Write-Warning "[release] $($pack.Name): build not found"
        continue
    }

    $destDir = Join-Path $versionDir $pack.Name
    Copy-Item -Recurse $packDir $destDir

    $classesJar = Join-Path $destDir "classes.jar"
    if (Test-Path $classesJar) {
        $hash = (Get-FileHash $classesJar -Algorithm SHA256).Hash.ToLower()
        $sizeBytes = (Get-Item $classesJar).Length
        $sha256Plugins[$pack.Name] = @{ sha256 = $hash; sizeBytes = $sizeBytes }
        Write-Host "  $($pack.Name): SHA-256 = $hash"
    }
}

# ---- 3. Version-printer APK ----
$pluginApk = Join-Path $projectRoot "plugins\plugin-version-printer\build\outputs\apk\debug\plugin-version-printer-debug.apk"
if (Test-Path $pluginApk) {
    Copy-Item $pluginApk (Join-Path $versionDir "plugin-manifest.apk")
    $apkHash = (Get-FileHash (Join-Path $versionDir "plugin-manifest.apk") -Algorithm SHA256).Hash.ToLower()
    Set-Content -Path (Join-Path $versionDir "plugin-manifest.apk.sha256") -Value $apkHash
    $sha256Plugins["plugin-manifest"] = @{ sha256 = $apkHash; sizeBytes = (Get-Item $pluginApk).Length }
    Write-Host "  plugin-manifest.apk: SHA-256 = $apkHash"
} else {
    Write-Warning "[release] plugin-version-printer APK not found"
}

# ---- 4. Generate SHA-256 manifest ----
$sha256Manifest = [ordered]@{
    tag = $Tag
    repo = "https://github.com/$Repo"
    generatedAt = (Get-Date -Format "yyyy-MM-ddTHH:mm:sszzz")
    plugins = $sha256Plugins
}

$manifestPath = Join-Path $versionDir "manifest-sha256.json"
$sha256Manifest | ConvertTo-Json -Depth 3 | Set-Content $manifestPath -Encoding UTF8
Write-Host ""
Write-Host "[release] SHA-256 manifest: $manifestPath" -ForegroundColor Green

# ---- 5. Package ZIP ----
$zipPath = Join-Path $releaseDir "foundry-plugins-$Tag.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath }

$tmpDir = Join-Path $releaseDir "_zip_tmp"
Remove-Item -Recurse -Force $tmpDir -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

foreach ($pack in $packs) {
    $packDir = Join-Path $projectRoot $pack.Path
    if (Test-Path $packDir) {
        $json = Get-Content (Join-Path $packDir "foundry-pack.json") -Raw -Encoding UTF8 | ConvertFrom-Json
        Copy-Item -Recurse $packDir (Join-Path $tmpDir $json.id) -ErrorAction SilentlyContinue
    }
}

if (Test-Path $pluginApk) {
    Copy-Item $pluginApk (Join-Path $tmpDir "plugin-manifest.apk")
}

Compress-Archive -Path "$tmpDir\*" -DestinationPath $zipPath -Force
Remove-Item -Recurse -Force $tmpDir

$zipSize = (Get-Item $zipPath).Length
Write-Host "[release] ZIP: $zipPath ($zipSize bytes)" -ForegroundColor Green

# ---- 6. GitHub Release ----
if ($DryRun) {
    Write-Host ""
    Write-Host "[release] DryRun -- artifacts in $versionDir" -ForegroundColor Yellow
    exit 0
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    Write-Warning "[release] gh CLI not installed. Artifacts ready in $releaseDir"
    Write-Host "  Upload ZIP: $zipPath" -ForegroundColor Yellow
    Write-Host "  Update plugin-repository.json with real sha256, then commit." -ForegroundColor Yellow
    exit 0
}

# Build release notes
$notesFile = Join-Path $releaseDir "release-notes.md"
$pluginItems = @()
foreach ($key in $sha256Plugins.Keys) {
    $info = $sha256Plugins[$key]
    $pluginItems += "- **$key** (SHA-256: $($info.sha256))"
}
$pluginListText = $pluginItems -join "`n"
$notesContent = @"
# $Tag

## Plugins
$pluginListText

## Deploy
`+"``"+`bash
adb push release/ /sdcard/Android/data/com.foundry.preview/files/FoundrySDK/
`+"``"+`

## SHA-256 Verification
See manifest-sha256.json
"@
Set-Content -Path $notesFile -Value $notesContent -Encoding UTF8

$draftFlag = if ($Draft) { "--draft" } else { "" }
Write-Host ""
Write-Host "[release] Creating GitHub Release..." -ForegroundColor Cyan

$releaseCmd = "gh release create $Tag --repo $Repo --title '$Tag' --notes-file '$notesFile' $draftFlag $zipPath"
Invoke-Expression $releaseCmd

if ($LASTEXITCODE -eq 0) {
    Write-Host "[release] OK: https://github.com/$Repo/releases/tag/$Tag" -ForegroundColor Green
    Write-Host ""
    Write-Host "[release] Next: update app/src/main/assets/plugin-repository.json:" -ForegroundColor Yellow
    Write-Host "  - Replace sha256 fields with values from manifest-sha256.json"
    Write-Host "  - Update version to '$Tag'"
} else {
    Write-Warning "[release] gh release create failed. Check gh auth status."
    Write-Host "  Artifacts at $versionDir" -ForegroundColor Yellow
}
