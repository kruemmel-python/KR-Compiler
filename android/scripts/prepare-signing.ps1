param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$Keystore,
    [string]$StorePassword = $(if ($env:KR_SIGNING_STORE_PASSWORD) { $env:KR_SIGNING_STORE_PASSWORD } else { 'android' }),
    [string]$KeyAlias = $(if ($env:KR_SIGNING_KEY_ALIAS) { $env:KR_SIGNING_KEY_ALIAS } else { 'kr-runner' }),
    [string]$KeyPassword = $(if ($env:KR_SIGNING_KEY_PASSWORD) { $env:KR_SIGNING_KEY_PASSWORD } else { $StorePassword })
)
$ErrorActionPreference = 'Stop'

$androidRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$localSigning = Join-Path $androidRoot '.local-signing'
$assets = Join-Path $androidRoot 'app\src\main\assets'
if (-not $Keystore) {
    $Keystore = if ($env:KR_SIGNING_STORE) { $env:KR_SIGNING_STORE } else { Join-Path $localSigning 'kr-development.keystore' }
}

function Resolve-JavaTool([string]$name) {
    if ($JavaHome) {
        $candidate = Join-Path $JavaHome ('bin\' + $name + '.exe')
        if (Test-Path -LiteralPath $candidate) { return $candidate }
    }
    $command = Get-Command ($name + '.exe'), $name -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($command) { return $command.Source }
    throw "$name wurde nicht gefunden. Bitte JDK 17 installieren oder JAVA_HOME setzen."
}

$keytool = Resolve-JavaTool 'keytool'
$javac = Resolve-JavaTool 'javac'
$java = Resolve-JavaTool 'java'
New-Item -ItemType Directory -Force $localSigning, $assets | Out-Null

if (-not (Test-Path -LiteralPath $Keystore)) {
    Write-Host 'Erzeuge lokalen Entwicklungsschlüssel (wird nicht in Git gespeichert) …'
    & $keytool -genkeypair -noprompt -storetype PKCS12 -keystore $Keystore `
        -storepass $StorePassword -keypass $KeyPassword -alias $KeyAlias `
        -keyalg RSA -keysize 2048 -validity 10000 `
        -dname 'CN=KR Compiler Development, O=KR Compiler, C=DE'
    if ($LASTEXITCODE -ne 0) { throw 'Lokaler Entwicklungsschlüssel konnte nicht erzeugt werden.' }
}

$keyOutput = Join-Path $assets 'kr-runner-key.pk8'
$certificateOutput = Join-Path $assets 'kr-runner-cert.der'
$classes = Join-Path $localSigning 'helper-classes'
New-Item -ItemType Directory -Force $classes | Out-Null
& $javac -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'ExportSigningMaterial.java')
if ($LASTEXITCODE -ne 0) { throw 'Hilfsprogramm für den Schlüsselexport konnte nicht kompiliert werden.' }
& $java -cp $classes ExportSigningMaterial $Keystore $StorePassword $KeyAlias $KeyPassword $keyOutput $certificateOutput
if ($LASTEXITCODE -ne 0) { throw 'Signiermaterial konnte nicht exportiert werden.' }

$env:KR_SIGNING_STORE = (Resolve-Path -LiteralPath $Keystore).Path
$env:KR_SIGNING_STORE_PASSWORD = $StorePassword
$env:KR_SIGNING_KEY_ALIAS = $KeyAlias
$env:KR_SIGNING_KEY_PASSWORD = $KeyPassword
Write-Host "Lokales Signiermaterial bereit: $Keystore"
