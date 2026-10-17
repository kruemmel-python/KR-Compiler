param(
    [string]$SourceDir = "C:\msys64\mingw64",
    [string]$TargetDir = "$PSScriptRoot\toolchain"
)

$ErrorActionPreference = "Stop"

Write-Host "=========================================================="
Write-Host "  KR Compiler IDE - Portable Toolchain Builder (Windows)   "
Write-Host "=========================================================="
Write-Host "Quelle: $SourceDir"
Write-Host "Ziel:   $TargetDir"

if (-not (Test-Path $SourceDir)) {
    throw "Quell-Toolchain nicht gefunden: $SourceDir"
}

$binTarget = Join-Path $TargetDir "bin"
$libTarget = Join-Path $TargetDir "lib"
$incTarget = Join-Path $TargetDir "include"

New-Item -ItemType Directory -Force $binTarget, $libTarget, $incTarget | Out-Null

# 1. Binaries & Runtime DLLs
Write-Host "[1/5] Kopiere Compiler-Binärdateien und DLLs..."
$binFiles = @(
    "g++.exe", "gcc.exe", "as.exe", "ld.exe", "mingw32-make.exe", "strip.exe",
    "libgmp-10.dll", "libmpfr-6.dll", "libmpc-3.dll", "libisl-23.dll",
    "zlib1.dll", "libzstd.dll", "libwinpthread-1.dll", "libgcc_s_seh-1.dll", "libstdc++-6.dll"
)
foreach ($f in $binFiles) {
    $src = Join-Path "$SourceDir\bin" $f
    if (Test-Path $src) {
        Copy-Item -LiteralPath $src -Destination $binTarget -Force
    }
}

# 2. GCC Internals (cc1plus, collect2, lto-wrapper, crtbegin/crtend)
Write-Host "[2/5] Kopiere GCC Internals (cc1plus, collect2)..."
$gccVerDir = Get-ChildItem -Path "$SourceDir\lib\gcc\x86_64-w64-mingw32" -Directory | Select-Object -First 1
if ($gccVerDir) {
    $gccTargetVer = Join-Path $TargetDir "lib\gcc\x86_64-w64-mingw32\$($gccVerDir.Name)"
    New-Item -ItemType Directory -Force $gccTargetVer | Out-Null
    
    $gccFiles = @("cc1.exe", "cc1plus.exe", "collect2.exe", "lto-wrapper.exe", "liblto_plugin.dll", "crtbegin.o", "crtend.o", "crtfastmath.o", "libgcc.a", "libgcc_eh.a")
    foreach ($gf in $gccFiles) {
        $gsrc = Join-Path $gccVerDir.FullName $gf
        if (Test-Path $gsrc) {
            Copy-Item -LiteralPath $gsrc -Destination $gccTargetVer -Force
        }
    }
    
    foreach ($sub in @("include", "include-fixed")) {
        $subSrc = Join-Path $gccVerDir.FullName $sub
        if (Test-Path $subSrc) {
            Copy-Item -LiteralPath $subSrc -Destination $gccTargetVer -Recurse -Force
        }
    }
}

# 3. C++ Standard-Header
Write-Host "[3/5] Kopiere C++ Standardbibliothek-Header..."
$cppInc = Join-Path "$SourceDir\include" "c++"
if (Test-Path $cppInc) {
    Copy-Item -LiteralPath $cppInc -Destination $incTarget -Recurse -Force
}

# 4. CRT und Windows SDK Header
Write-Host "[4/5] Kopiere Windows- und C-Runtime-Header..."
$headerSubdirs = @("sys", "sec_api", "psdk_inc", "GL", "KHR")
foreach ($sd in $headerSubdirs) {
    $sdSrc = Join-Path "$SourceDir\include" $sd
    if (Test-Path $sdSrc) {
        Copy-Item -LiteralPath $sdSrc -Destination $incTarget -Recurse -Force
    }
}
Get-ChildItem -Path "$SourceDir\include" -File -Filter "*.h" | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $incTarget -Force
}

# 5. Core Libraries & CRT Objects
Write-Host "[5/5] Kopiere Core-Bibliotheken und CRT-Objekte..."
$libFiles = @(
    "crt2.o", "crt2u.o", "dllcrt2.o", "gcrt2.o",
    "libkernel32.a", "libmsvcrt.a", "libuser32.a", "libstdc++.a", "libwinpthread.a",
    "libshell32.a", "libgdi32.a", "libadvapi32.a", "libole32.a", "libuuid.a",
    "libws2_32.a", "libmingw32.a", "libmingwex.a", "libmoldname.a"
)
foreach ($lf in $libFiles) {
    $lsrc = Join-Path "$SourceDir\lib" $lf
    if (Test-Path $lsrc) {
        Copy-Item -LiteralPath $lsrc -Destination $libTarget -Force
    }
}

# Markiere Bereitschafts-Stempel
$stamp = Join-Path $TargetDir "kr-toolchain.stamp"
Set-Content -Path $stamp -Value "KR-Toolchain-Windows-x64-v4.0.3"

$totalMb = (Get-ChildItem -Path $TargetDir -Recurse -File | Measure-Object -Property Length -Sum).Sum / 1MB
Write-Host "=========================================================="
Write-Host "[ERFOLG] Portable Toolchain erstellt in: $TargetDir"
Write-Host "Gesamtgröße: $([math]::Round($totalMb, 2)) MB"
Write-Host "=========================================================="
