param(
    [string]$Destination = (Join-Path $PSScriptRoot 'runtime\jdk'),
    [string]$Cache = (Join-Path (Split-Path -Parent $PSScriptRoot) '.windows-runtime-cache')
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$url = 'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
$sha256 = 'f9d6e191ab098c0d416e7d588a24420a8621cd2f4720dab2459b8b7b2d2d8b4e'
$archive = Join-Path $Cache 'OpenJDK21U-jdk_x64_windows_hotspot_21.0.12.1_1.zip'
$extract = Join-Path $Cache 'extract'
New-Item -ItemType Directory -Force $Cache, (Join-Path $Cache 'tmp') | Out-Null
$env:TEMP = Join-Path $Cache 'tmp'
$env:TMP = $env:TEMP

if (-not (Test-Path -LiteralPath $archive) -or
    (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sha256) {
    $partial = $archive + '.part'
    Invoke-WebRequest -Uri $url -OutFile $partial -UseBasicParsing
    $actual = (Get-FileHash -LiteralPath $partial -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $sha256) { throw "Temurin JDK checksum mismatch: $actual" }
    Move-Item -LiteralPath $partial -Destination $archive -Force
}

$resolvedDestination = [IO.Path]::GetFullPath($Destination)
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'runtime'))
if (-not $resolvedDestination.StartsWith($allowedRoot, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Unsafe runtime destination: $resolvedDestination"
}
if (Test-Path -LiteralPath $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
New-Item -ItemType Directory -Force $extract | Out-Null
Expand-Archive -LiteralPath $archive -DestinationPath $extract -Force
$root = Get-ChildItem -LiteralPath $extract -Directory | Select-Object -First 1
if (-not $root -or -not (Test-Path -LiteralPath (Join-Path $root.FullName 'bin\java.exe'))) {
    throw 'Temurin archive does not contain a usable Windows x64 JDK.'
}
if (Test-Path -LiteralPath $Destination) { Remove-Item -LiteralPath $Destination -Recurse -Force }
New-Item -ItemType Directory -Force (Split-Path -Parent $Destination) | Out-Null
Move-Item -LiteralPath $root.FullName -Destination $Destination
& (Join-Path $Destination 'bin\java.exe') -version
Write-Host "Portable Windows Java runtime installed at $Destination"
