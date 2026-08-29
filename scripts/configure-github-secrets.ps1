param(
    [string]$Repository = "XClear0/Maxfield-Link-Overlay",
    [string]$PropertiesFile = (Join-Path $PSScriptRoot "..\keystore.properties")
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "GitHub CLI (gh) is not installed."
}

gh auth status --hostname github.com
if ($LASTEXITCODE -ne 0) {
    throw "GitHub CLI is not authenticated. Run: gh auth login --hostname github.com --web"
}

$resolvedPropertiesFile = (Resolve-Path -LiteralPath $PropertiesFile).Path
$properties = @{}
foreach ($line in Get-Content -LiteralPath $resolvedPropertiesFile) {
    if ($line -match '^\s*([^#!][^=]*)=(.*)$') {
        $properties[$matches[1].Trim()] = $matches[2].Trim()
    }
}

foreach ($requiredName in 'storeFile', 'storePassword', 'keyAlias', 'keyPassword') {
    if ([string]::IsNullOrWhiteSpace($properties[$requiredName])) {
        throw "Missing '$requiredName' in $resolvedPropertiesFile"
    }
}

$keystorePath = $properties.storeFile
if (-not [System.IO.Path]::IsPathRooted($keystorePath)) {
    $keystorePath = Join-Path (Split-Path $resolvedPropertiesFile) $keystorePath
}
$keystorePath = (Resolve-Path -LiteralPath $keystorePath).Path
$keystoreBase64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystorePath))

$secrets = [ordered]@{
    RELEASE_KEYSTORE_BASE64 = $keystoreBase64
    RELEASE_STORE_PASSWORD  = $properties.storePassword
    RELEASE_KEY_ALIAS       = $properties.keyAlias
    RELEASE_KEY_PASSWORD    = $properties.keyPassword
}

foreach ($entry in $secrets.GetEnumerator()) {
    $entry.Value | gh secret set $entry.Key --repo $Repository
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to set GitHub Actions secret '$($entry.Key)'."
    }
}

Write-Host "Configured release-signing secrets for $Repository."
