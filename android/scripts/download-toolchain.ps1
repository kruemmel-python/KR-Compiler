param(
    [string]$Output = (Join-Path $PSScriptRoot '..\app\src\main\assets\toolchain.zip'),
    [string]$Url = 'https://github.com/kruemmel-python/KR-Compiler/releases/download/v3.1.0/KR-Compiler-Android-toolchain-v3.1.0.zip',
    [string]$Sha256 = 'bb1b24d80d7ffbbcbe24e96ada527370f97bd9d169a97ffcd9d78b9670810b08'
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

if (Test-Path -LiteralPath $Output) {
    $existingHash = (Get-FileHash -LiteralPath $Output -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($existingHash -eq $Sha256.ToLowerInvariant()) {
        Write-Host 'Compiler-Laufzeit ist bereits vollständig vorhanden.'
        return
    }
    Write-Host 'Vorhandene Compiler-Laufzeit hat nicht die erwartete Prüfsumme und wird ersetzt.'
}

$directory = Split-Path -Parent $Output
New-Item -ItemType Directory -Force $directory | Out-Null
$temporary = $Output + '.download'
try {
    Write-Host 'Lade die Compiler-Laufzeit (ca. 385 MiB) aus dem GitHub-Release …'
    Invoke-WebRequest -Uri $Url -OutFile $temporary -UseBasicParsing
    $downloadedHash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($downloadedHash -ne $Sha256.ToLowerInvariant()) {
        throw "Prüfsumme der Compiler-Laufzeit ist falsch: $downloadedHash"
    }
    Move-Item -LiteralPath $temporary -Destination $Output -Force
    Write-Host "Compiler-Laufzeit bereit: $Output"
} finally {
    if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary -Force }
}
