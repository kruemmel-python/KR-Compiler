param(
    [string]$AndroidSdk = $(if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } elseif ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { Join-Path $env:LOCALAPPDATA 'Android\Sdk' }),
    [string]$Output = (Join-Path $PSScriptRoot '..\app\src\main\assets\android-graphics-headers.zip')
)
$ErrorActionPreference = 'Stop'
$ndkRoot = Get-ChildItem (Join-Path $AndroidSdk 'ndk') -Directory |
    Sort-Object { [version]$_.Name } -Descending | Select-Object -First 1
if (-not $ndkRoot) { throw "Android NDK wurde nicht gefunden unter: $AndroidSdk\ndk" }
$include = Join-Path $ndkRoot.FullName 'toolchains\llvm\prebuilt\windows-x86_64\sysroot\usr\include'
$temporary = Join-Path ([IO.Path]::GetTempPath()) ("kr-android-graphics-" + [guid]::NewGuid().ToString('N'))
$target = Join-Path $temporary 'usr\include'
New-Item -ItemType Directory -Force $target | Out-Null
try {
    foreach ($directory in 'EGL','GLES','GLES2','GLES3','KHR') {
        $source = Join-Path $include $directory
        if (-not (Test-Path $source)) { throw "Android-NDK-Header fehlen: $source" }
        Copy-Item -LiteralPath $source -Destination $target -Recurse
    }
    $parent = Split-Path -Parent $Output
    New-Item -ItemType Directory -Force $parent | Out-Null
    if (Test-Path $Output) { Remove-Item -LiteralPath $Output -Force }
    Compress-Archive -Path (Join-Path $temporary '*') -DestinationPath $Output -CompressionLevel Optimal
    Write-Host "Erstellt: $Output"
    Write-Host "SHA-256: $((Get-FileHash $Output -Algorithm SHA256).Hash.ToLowerInvariant())"
} finally {
    if (Test-Path $temporary) { Remove-Item -LiteralPath $temporary -Recurse -Force }
}
