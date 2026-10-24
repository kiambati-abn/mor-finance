# Installs (or updates) the latest Morph release on the phone plugged in over USB.
# Usage (Windows PowerShell):  .\scripts\install.ps1 -Repo <owner>/mor-finance
# Needs: adb (Android platform-tools) and gh (GitHub CLI, logged in with `gh auth login`).
param([string]$Repo = $env:MORPH_REPO)
$ErrorActionPreference = "Stop"

if (-not $Repo) {
    $url = git -C (Join-Path $PSScriptRoot "..") config --get remote.origin.url 2>$null
    if ($url) { $Repo = ($url -replace '^(git@github.com:|https://github.com/)', '') -replace '\.git$', '' }
}
if (-not $Repo) { Write-Host "Tell me the repo: .\scripts\install.ps1 -Repo <owner>/mor-finance"; exit 1 }
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) { Write-Host "adb not found. Install Android platform-tools: https://developer.android.com/tools/releases/platform-tools"; exit 1 }
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { Write-Host "gh not found. Install the GitHub CLI: https://cli.github.com/ then run: gh auth login"; exit 1 }

$state = (adb get-state 2>$null)
if ($state -ne "device") {
    Write-Host "No phone found. Plug it in, unlock it, and tap 'Allow' on the USB debugging prompt. Then run this again."
    adb devices
    exit 1
}

$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("morph-" + [guid]::NewGuid())
New-Item -ItemType Directory -Path $tmp | Out-Null
try {
    Write-Host "Downloading the latest Morph from $Repo..."
    gh release download --repo $Repo --pattern "*.apk" --dir $tmp
    $apk = Get-ChildItem $tmp -Filter *.apk | Select-Object -First 1
    Write-Host "Installing $($apk.Name) (your data is kept)..."
    adb install -r $apk.FullName
    adb shell monkey -p com.morphlabs.morph -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host "Done"
} finally {
    Remove-Item -Recurse -Force $tmp
}
