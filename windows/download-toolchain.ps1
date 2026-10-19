param(
    [string]$TargetDir = "$PSScriptRoot\toolchain",
    [string]$Url = "https://github.com/kruemmel-python/KR-Compiler/releases/download/v4.1.0/KR-Compiler-Windows-toolchain-v4.1.0.zip"
)

$ErrorActionPreference = "Stop"

$stamp = Join-Path $TargetDir "kr-toolchain.stamp"
if (Test-Path $stamp) {
    Write-Host "[OK] Integrierte Windows-Toolchain ist bereits vollständig vorhanden: $TargetDir"
    return
}

Write-Host "=========================================================="
Write-Host "  KR Compiler IDE - Integrierte Toolchain für Windows      "
Write-Host "=========================================================="
Write-Host "Lade portable Compiler-Toolchain herunter..."

New-Item -ItemType Directory -Force $TargetDir | Out-Null
$zipFile = Join-Path $PSScriptRoot "toolchain.zip"

try {
    Write-Host "URL: $Url"
    Invoke-WebRequest -Uri $Url -OutFile $zipFile -UseBasicParsing
    Write-Host "Entpacke Toolchain nach $TargetDir …"
    Expand-Archive -LiteralPath $zipFile -DestinationPath $TargetDir -Force
    Set-Content -Path $stamp -Value "KR-Toolchain-Windows-x64-v4.1.0"
    Write-Host "[ERFOLG] Toolchain erfolgreich eingerichtet!"
} catch {
    Write-Warning "Automatischer Download fehlgeschlagen: $_"
    Write-Host "Prüfe lokale MSYS2-Quelle..."
    $bootstrap = Join-Path $PSScriptRoot "bootstrap-toolchain.ps1"
    if (Test-Path $bootstrap) {
        & $bootstrap
    }
} finally {
    if (Test-Path $zipFile) { Remove-Item $zipFile -Force }
}
