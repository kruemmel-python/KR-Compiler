param(
    [string]$RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path,
    [string]$Output = (Join-Path $PSScriptRoot '..\app\src\main\assets\toolchain.zip')
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$cache = Join-Path $RepositoryRoot '.android-cache'
$indexFile = Join-Path $cache 'Packages'
$download = Join-Path $cache 'termux-debs'
$extract = Join-Path $cache 'termux-extract'
$packageRoot = Join-Path $cache 'termux-package-root'
$stage = Join-Path $cache 'toolchain-stage'
New-Item -ItemType Directory -Force $cache, $download | Out-Null

$indexUrl = 'https://packages.termux.dev/apt/termux-main/dists/stable/main/binary-aarch64/Packages'
if (-not (Test-Path $indexFile)) { Invoke-WebRequest $indexUrl -OutFile $indexFile -UseBasicParsing }

function Parse-ControlBlock([string]$block) {
    $result = @{}; $last = $null
    foreach ($line in ($block -split "`r?`n")) {
        if ($line -match '^([^:]+):\s?(.*)$') { $last = $matches[1]; $result[$last] = $matches[2] }
        elseif ($last -and $line.StartsWith(' ')) { $result[$last] += "`n" + $line.Substring(1) }
    }
    return $result
}

$packages = @{}
foreach ($block in ([IO.File]::ReadAllText($indexFile) -split "(?:`r?`n){2,}")) {
    $item = Parse-ControlBlock $block
    if ($item.Package) { $packages[$item.Package] = $item }
}

$selected = [Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
function Add-Package([string]$name) {
    if ($selected.Contains($name)) { return }
    if (-not $packages.ContainsKey($name)) { throw "Termux-Paket nicht gefunden: $name" }
    [void]$selected.Add($name)
    $depends = $packages[$name].Depends
    if (-not $depends) { return }
    foreach ($raw in ($depends -split ',')) {
        $dependency = (($raw -split '\|')[0] -replace '\s*\([^)]*\)', '').Trim()
        if ($dependency) { Add-Package $dependency }
    }
}
Add-Package 'clang'
Add-Package 'cmake'
Add-Package 'ninja'
Add-Package 'pkg-config'
Add-Package 'git'
Add-Package 'ca-certificates'
Add-Package 'dash'
Add-Package 'rust'
Add-Package 'rust-src'
Add-Package 'golang'

if (Test-Path $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
if (Test-Path $packageRoot) { Remove-Item -LiteralPath $packageRoot -Recurse -Force }
if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Force $extract, (Join-Path $packageRoot 'data\data\com.termux\files\usr'), (Join-Path $stage 'usr') | Out-Null

$baseUrl = 'https://packages.termux.dev/apt/termux-main/'
$symlinks = [Collections.Generic.List[object]]::new()
foreach ($name in ($selected | Sort-Object)) {
    $package = $packages[$name]
    # Debian epochs use ':' in archive names; ':' is an NTFS stream separator.
    # Keep the remote URL unchanged but sanitize the local cache filename.
    $localName = ([IO.Path]::GetFileName($package.Filename) -replace ':', '_')
    $deb = Join-Path $download $localName
    if (-not (Test-Path $deb) -or (Get-Item $deb).Length -ne [long]$package.Size) {
        Write-Host "Lade $name $($package.Version) …"
        Invoke-WebRequest ($baseUrl + $package.Filename) -OutFile $deb -UseBasicParsing
    }
    $packageDir = Join-Path $extract $name
    New-Item -ItemType Directory -Force $packageDir | Out-Null
    & bsdtar -xf $deb -C $packageDir
    if ($LASTEXITCODE -ne 0) { throw "Debian-Paket kann nicht geöffnet werden: $deb" }
    $data = Get-ChildItem $packageDir -File | Where-Object Name -Like 'data.tar*' | Select-Object -First 1
    if (-not $data) { throw "Datenarchiv fehlt in $deb" }
    & bsdtar -xf $data.FullName -C $packageRoot
    # Windows normally refuses Unix symlink creation. Materialize each link as a
    # real file/directory after all regular archive entries have been extracted.
    $listing = & bsdtar -tvf $data.FullName
    foreach ($line in $listing) {
        if ($line -notmatch '\s(\./\S+) -> (\S+)$') { continue }
        $relative = $matches[1].Substring(2).Replace('/', '\')
        $linkTarget = $matches[2]
        $destination = Join-Path $packageRoot $relative
        if ($linkTarget.StartsWith('/')) { $source = Join-Path $packageRoot $linkTarget.TrimStart('/').Replace('/', '\') }
        else { $source = Join-Path (Split-Path -Parent $destination) $linkTarget.Replace('/', '\') }
        $symlinks.Add([pscustomobject]@{ Relative=$relative; Target=$linkTarget; Destination=$destination; Source=$source })
    }
}

$pending = [Collections.Generic.List[object]]::new()
foreach ($link in $symlinks) { $pending.Add($link) }
for ($pass = 0; $pass -lt 12 -and $pending.Count -gt 0; $pass++) {
    $next = [Collections.Generic.List[object]]::new()
    foreach ($link in $pending) {
        if (-not (Test-Path -LiteralPath $link.Source)) { $next.Add($link); continue }
        if (Test-Path -LiteralPath $link.Destination) { Remove-Item -LiteralPath $link.Destination -Force -Recurse }
        Copy-Item -LiteralPath $link.Source -Destination $link.Destination -Recurse
    }
    if ($next.Count -eq $pending.Count) { $pending = $next; break }
    $pending = $next
}
foreach ($link in $pending) {
    if ($link.Relative -match '\\(bin|lib|libexec)\\') { throw "Symlink-Ziel fehlt: $($link.Relative) -> $($link.Target)" }
}

$termuxPrefix = Join-Path $packageRoot 'data\data\com.termux\files\usr'
Get-ChildItem -LiteralPath $termuxPrefix -Force | ForEach-Object {
    Move-Item -LiteralPath $_.FullName -Destination (Join-Path $stage 'usr') -Force
}

# Materialize the compiler/linker aliases because ZIP does not preserve Unix symlinks reliably.
$bin = Join-Path $stage 'usr\bin'
$clangReal = Get-ChildItem $bin -File | Where-Object Name -Match '^clang-[0-9]+$' | Select-Object -First 1
if (-not $clangReal) { $clangReal = Get-Item (Join-Path $bin 'clang') }
foreach ($alias in 'clang','clang++','cc','c++') { Copy-Item -LiteralPath $clangReal.FullName -Destination (Join-Path $bin $alias) -Force }
$lldReal = Get-ChildItem $bin -File | Where-Object Name -Match '^lld$|^ld\.lld$' | Select-Object -First 1
if ($lldReal) {
    foreach ($alias in 'lld','ld.lld') {
        $aliasPath = Join-Path $bin $alias
        if ($lldReal.FullName -ne $aliasPath) { Copy-Item -LiteralPath $lldReal.FullName -Destination $aliasPath -Force }
    }
}
$dlltool = Join-Path $bin 'llvm-dlltool'
if (Test-Path $dlltool) {
    Copy-Item -LiteralPath $dlltool -Destination (Join-Path $bin 'x86_64-w64-mingw32-dlltool') -Force
}
# These packages also use symlink-style command aliases. Materialize the names
# the Android build engine invokes so the ZIP remains independent of symlinks.
$pkgconf = Get-ChildItem $bin -File | Where-Object Name -Match '^pkgconf$|^pkg-config$' | Select-Object -First 1
if ($pkgconf) {
    $pkgConfigPath = Join-Path $bin 'pkg-config'
    if ($pkgconf.FullName -ne $pkgConfigPath) { Copy-Item -LiteralPath $pkgconf.FullName -Destination $pkgConfigPath -Force }
}

# Ninja invokes the shell at @TERMUX_PREFIX@/bin/sh. Materialize sh from dash so
# build rules and try_compile commands can be spawned reliably.
$dashReal = Get-ChildItem $bin -File | Where-Object Name -Match '^dash$' | Select-Object -First 1
if ($dashReal) {
    Copy-Item -LiteralPath $dashReal.FullName -Destination (Join-Path $bin 'sh') -Force
}

# The Debian Git package contains roughly 150 symlinks to the main Git binary.
# Materializing all of them would duplicate the same 3.5 MiB executable in the
# ZIP and inflate the APK by hundreds of megabytes. Git dispatches built-ins
# internally; only the genuinely separate helpers (notably remote-http/https)
# are required in libexec.
$gitBinary = Join-Path $bin 'git'
$gitCore = Join-Path $stage 'usr\libexec\git-core'
if ((Test-Path $gitBinary) -and (Test-Path $gitCore)) {
    $gitLength = (Get-Item $gitBinary).Length
    Get-ChildItem $gitCore -File | Where-Object Length -EQ $gitLength | Remove-Item -Force
}

# Safeguard Termux CMake against unhandled missing /include/android/api-level.h
$cmakeDetermineSystem = Join-Path $stage 'usr\share\cmake-4.4\Modules\CMakeDetermineSystem.cmake'
if (Test-Path $cmakeDetermineSystem) {
    $content = [IO.File]::ReadAllText($cmakeDetermineSystem)
    if ($content.Contains('file(READ ${_ANDROID_API_LEVEL_H} _ANDROID_API_LEVEL_H_CONTENT)')) {
        $content = $content.Replace(
            "file(READ `${_ANDROID_API_LEVEL_H} _ANDROID_API_LEVEL_H_CONTENT)",
            "if(EXISTS `"`${_ANDROID_API_LEVEL_H}`")`n          file(READ `${_ANDROID_API_LEVEL_H} _ANDROID_API_LEVEL_H_CONTENT)"
        ).Replace(
            "if(_ANDROID_API)`n          set(CMAKE_SYSTEM_VERSION `"`${_ANDROID_API}`")`n        endif()",
            "if(_ANDROID_API)`n          set(CMAKE_SYSTEM_VERSION `"`${_ANDROID_API}`")`n        endif()`n        endif()"
        )
        [IO.File]::WriteAllText($cmakeDetermineSystem, $content)
    }
}

function Resolve-HostRoot {
    $direct = Join-Path $RepositoryRoot 'host'
    if (Test-Path $direct) { return $direct }
    # Release-Arbeitskopien liegen neben dem großen, nicht versionierten host-Verzeichnis.
    $shared = Join-Path (Split-Path -Parent (Split-Path -Parent $RepositoryRoot)) 'host'
    if (Test-Path $shared) { return $shared }
    throw 'Host-Zielsysteme fehlen. Erwartet wurde host/ im Repository oder im gemeinsamen Projektordner.'
}
$hostRoot = Resolve-HostRoot

Write-Host 'Füge Windows-x64-Zielsystem hinzu …'
$windowsSource = Join-Path $hostRoot 'windows-x64\toolchain'
$windowsTarget = Join-Path $stage 'targets\windows'
New-Item -ItemType Directory -Force $windowsTarget | Out-Null
Copy-Item (Join-Path $windowsSource 'include') $windowsTarget -Recurse
Copy-Item (Join-Path $windowsSource 'lib') $windowsTarget -Recurse
Copy-Item (Join-Path $windowsSource 'x86_64-w64-mingw32') $windowsTarget -Recurse

$resourceDir = Get-ChildItem (Join-Path $stage 'usr\lib\clang') -Directory |
    Where-Object Name -Match '^\d+(\.\d+)*$' |
    Sort-Object { [version]($(if ($_.Name.Contains('.')) { $_.Name } else { $_.Name + '.0' })) } -Descending |
    Select-Object -First 1
if (-not $resourceDir) { throw 'Numerischer Clang-Ressourcenordner wurde nicht gefunden.' }
# The Android-hosted clang driver uses the LLVM cross-target layout instead
# of the Windows-hosted layout. It requests this exact archive path.
$windowsCrossRuntime = Join-Path $resourceDir.FullName 'lib\x86_64-w64-windows-gnu'
New-Item -ItemType Directory -Force $windowsCrossRuntime | Out-Null
Copy-Item (Join-Path $windowsSource 'lib\clang\23\lib\windows\libclang_rt.builtins-x86_64.a') `
    (Join-Path $windowsCrossRuntime 'libclang_rt.builtins.a') -Force

Write-Host 'Füge Linux-x64-Zielsystem hinzu …'
$linuxTarget = Join-Path $stage 'targets\linux'
New-Item -ItemType Directory -Force (Join-Path $linuxTarget 'gcc') | Out-Null
Copy-Item (Join-Path $hostRoot 'linux-x64\sysroot') $linuxTarget -Recurse
Copy-Item (Join-Path $hostRoot 'linux-x64\backend\include') (Join-Path $linuxTarget 'gcc') -Recurse
Copy-Item (Join-Path $hostRoot 'linux-x64\backend\lib') (Join-Path $linuxTarget 'gcc') -Recurse

# Add the X11 development subset required by SDL GUI applications. The helper
# downloads fixed Ubuntu 24.04 packages, verifies their hashes and materializes
# Unix symlinks so the Windows-created ZIP remains portable on Android.
$linuxGuiRoot = Join-Path $cache 'linux-gui-root'
& (Join-Path $PSScriptRoot 'prepare-linux-gui-sysroot.ps1') -Output $linuxGuiRoot
if ($LASTEXITCODE -ne 0) { throw 'Linux-GUI-Sysroot konnte nicht vorbereitet werden.' }
Copy-Item (Join-Path $linuxGuiRoot '*') (Join-Path $linuxTarget 'sysroot') -Recurse -Force

# rust-src stammt aus demselben Termux-Build wie rustc. Cargo baut daraus die
# Desktop-Standardbibliotheken einmalig und ABI-genau mit `-Z build-std`.

# Termux is built for a fixed 31-byte prefix. The application id deliberately has
# the same length, allowing a safe in-place replacement without corrupting ELF files.
$old = [Text.Encoding]::ASCII.GetBytes('/data/data/com.termux/files/usr')
$new = [Text.Encoding]::ASCII.GetBytes('/data/data/kr.compile/files/usr')
if ($old.Length -ne $new.Length) { throw 'Interner Präfix muss exakt gleich lang sein.' }
if (-not ('KrPrefixPatcher' -as [type])) {
    Add-Type @'
using System;
using System.IO;
public static class KrPrefixPatcher {
    public static void Replace(string path, byte[] find, byte[] replacement) {
        byte[] data = File.ReadAllBytes(path);
        bool changed = false;
        for (int i = 0; i <= data.Length - find.Length; i++) {
            if (data[i] != find[0]) continue;
            bool match = true;
            for (int j = 1; j < find.Length; j++) if (data[i + j] != find[j]) { match = false; break; }
            if (!match) continue;
            Buffer.BlockCopy(replacement, 0, data, i, replacement.Length);
            changed = true; i += find.Length - 1;
        }
        if (changed) File.WriteAllBytes(path, data);
    }
}
'@
}
foreach ($file in (Get-ChildItem $stage -Recurse -File)) {
    [KrPrefixPatcher]::Replace($file.FullName, $old, $new)
}

$outputDirectory = Split-Path -Parent $Output
New-Item -ItemType Directory -Force $outputDirectory | Out-Null
if (Test-Path $Output) { Remove-Item -LiteralPath $Output -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[IO.Compression.ZipFile]::CreateFromDirectory($stage, $Output, [IO.Compression.CompressionLevel]::Optimal, $false)
$hash = (Get-FileHash $Output -Algorithm SHA256).Hash.ToLowerInvariant()
Write-Host "Erstellt: $Output"
Write-Host "SHA-256: $hash"
$siblingOutput = Join-Path $RepositoryRoot 'android\app\src\main\assets\toolchain.zip'
if ((Test-Path (Split-Path -Parent $siblingOutput)) -and ((Resolve-Path $Output).Path -ne (Resolve-Path $siblingOutput).Path)) {
    Copy-Item -LiteralPath $Output -Destination $siblingOutput -Force
    Write-Host "Kopiert nach: $siblingOutput"
}
