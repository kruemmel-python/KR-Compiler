param([Parameter(Mandatory=$true)][string]$Apk, [string]$Output="D:\android_project\build\adaptive-compute\android-device.json")
$ErrorActionPreference = "Stop"
$adb = Get-ChildItem -Path "D:\android_project" -Filter adb.exe -File -Recurse -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $adb) { throw "ADB ist nicht im portablen Projektbestand vorhanden." }
$devices = & $adb.FullName devices
$serial = ($devices | Select-String "\tdevice$").Line.Split("`t")[0] | Select-Object -First 1
if (-not $serial) { throw "Kein freigegebenes physisches Android-Gerät angeschlossen." }
if (-not (Test-Path -LiteralPath $Apk)) { throw "APK nicht gefunden: $Apk" }
& $adb.FullName -s $serial install -r $Apk | Out-Null
$props = @{
  schema=1; status="DEVICE_DISCOVERED"; serial=$serial
  manufacturer=(& $adb.FullName -s $serial shell getprop ro.product.manufacturer).Trim()
  model=(& $adb.FullName -s $serial shell getprop ro.product.model).Trim()
  android=(& $adb.FullName -s $serial shell getprop ro.build.version.release).Trim()
  api=(& $adb.FullName -s $serial shell getprop ro.build.version.sdk).Trim()
  gpu=(& $adb.FullName -s $serial shell getprop ro.hardware.egl).Trim()
  note="Geräteerkennung bestanden; GPU-Ergebnisvalidierung benötigt einen gestarteten VERIFY-Testlauf und dessen Telemetriedatei."
}
$parent = Split-Path -Parent $Output
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$props | ConvertTo-Json | Set-Content -LiteralPath $Output -Encoding UTF8
Write-Output $Output
