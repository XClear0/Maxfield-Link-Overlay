param(
    [string]$KeyAlias = "maxfield-overlay",
    [int]$ValidityDays = 10000
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command keytool -ErrorAction SilentlyContinue)) {
    throw "keytool is not installed. Install JDK 17 or newer first."
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$signingDirectory = Join-Path $repositoryRoot ".signing"
$keystorePath = Join-Path $signingDirectory "maxfield-overlay-release.jks"
$propertiesPath = Join-Path $repositoryRoot "keystore.properties"

if ((Test-Path -LiteralPath $keystorePath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw "Release signing files already exist. Refusing to overwrite the app's signing identity."
}

New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null

$randomBytes = New-Object byte[] 36
[Security.Cryptography.RandomNumberGenerator]::Fill($randomBytes)
$password = [Convert]::ToBase64String($randomBytes).Replace("+", "-").Replace("/", "_").TrimEnd("=")

& keytool -genkeypair `
    -keystore $keystorePath `
    -storetype PKCS12 `
    -storepass $password `
    -keypass $password `
    -alias $KeyAlias `
    -keyalg RSA `
    -keysize 4096 `
    -validity $ValidityDays `
    -dname "CN=Maxfield Link Overlay, OU=Release, O=XClear0, C=CN"

if ($LASTEXITCODE -ne 0) {
    throw "keytool failed to create the release keystore."
}

@(
    "storeFile=.signing/maxfield-overlay-release.jks"
    "storePassword=$password"
    "keyAlias=$KeyAlias"
    "keyPassword=$password"
) | Set-Content -LiteralPath $propertiesPath -Encoding UTF8

Write-Host "Created release keystore and local signing configuration."
Write-Host "Back up these two files securely before publishing:"
Write-Host "  $keystorePath"
Write-Host "  $propertiesPath"
