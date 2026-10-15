param(
    [string]$AndroidSdk = $env:ANDROID_HOME,
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [switch]$SkipToolchainDownload
)
$ErrorActionPreference = 'Stop'
if (-not $AndroidSdk) { $AndroidSdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if (-not $JavaHome) {
    $javac = Get-Command javac -ErrorAction SilentlyContinue
    if ($javac) { $JavaHome = Split-Path -Parent (Split-Path -Parent $javac.Source) }
}
if (-not $JavaHome -or -not (Test-Path (Join-Path $JavaHome 'bin\javac.exe'))) {
    throw 'JDK 17 wurde nicht gefunden. Bitte JAVA_HOME setzen.'
}
if (-not (Test-Path -LiteralPath $AndroidSdk)) { throw "Android SDK wurde nicht gefunden: $AndroidSdk" }
$env:ANDROID_HOME = $AndroidSdk
$env:JAVA_HOME = $JavaHome

$toolchainAsset = Join-Path $PSScriptRoot '..\app\src\main\assets\toolchain.zip'
if (-not $SkipToolchainDownload -and -not (Test-Path -LiteralPath $toolchainAsset)) {
    & (Join-Path $PSScriptRoot 'download-toolchain.ps1')
} elseif (-not $SkipToolchainDownload) {
    Write-Host 'Vorhandene Compiler-Laufzeit wird verwendet. Für eine Aktualisierung zuerst package-toolchain.ps1 ausführen.'
}
& (Join-Path $PSScriptRoot 'prepare-signing.ps1') -JavaHome $JavaHome

$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$gradleWrapper = Join-Path $androidRoot 'gradlew.bat'
if (-not (Test-Path -LiteralPath $gradleWrapper)) { throw 'Gradle Wrapper fehlt im Android-Projekt.' }
& $gradleWrapper -p $androidRoot :app:assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'APK-Build fehlgeschlagen.' }
$apk = Resolve-Path (Join-Path $PSScriptRoot '..\app\build\outputs\apk\release\app-release.apk')
Write-Host "Fertig: $apk"

$releaseName = 'KR-Compiler-Android-IDE-v4.0.1.apk'
$rootApk = Join-Path $RepositoryRoot $releaseName
$localApk = Join-Path $androidRoot $releaseName
Copy-Item -LiteralPath $apk -Destination $rootApk -Force
Copy-Item -LiteralPath $apk -Destination $localApk -Force
$hash = (Get-FileHash $localApk -Algorithm SHA256).Hash.ToLowerInvariant()
[IO.File]::WriteAllText($rootApk + '.sha256', "$hash  $releaseName`n")
[IO.File]::WriteAllText($localApk + '.sha256', "$hash  $releaseName`n")
Write-Host "Aktualisiert: $localApk (SHA256: $hash)"
