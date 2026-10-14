param(
    [string]$Output = (Join-Path $PSScriptRoot '..\.android-cache\linux-gui-root')
)
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

# Ubuntu 24.04 packages matching the glibc 2.39 sysroot bundled by KR Compiler.
$packages = @(
    @{ File='pool/main/libx/libx11/libx11-dev_1.8.7-1build1_amd64.deb'; Sha='1969e200607ffe34070b92fe1bd3ba76ac2e2bd2f3a54530800c5872076fad24' },
    @{ File='pool/main/libx/libx11/libx11-6_1.8.7-1build1_amd64.deb'; Sha='397f84347476a3c5786b39f3ff6f0f82866eb3d8be6d2ad3efeadf019efe5b80' },
    @{ File='pool/main/libx/libxext/libxext-dev_1.3.4-1build2_amd64.deb'; Sha='b5bdb6724f228cb8eee8cf39cbbe1289dd3f8f06dd022748312df7844c94dcf2' },
    @{ File='pool/main/libx/libxext/libxext6_1.3.4-1build2_amd64.deb'; Sha='45783969a9ece9d7b7b733b8c60981584c53c6bc5ee3b42d295d2f80d1285679' },
    @{ File='pool/main/libx/libxau/libxau-dev_1.0.9-1build6_amd64.deb'; Sha='46783c6af2d87c06394d55dfaa21dac07396f13cfc5ea6afbd9313ab49fc0e50' },
    @{ File='pool/main/libx/libxau/libxau6_1.0.9-1build6_amd64.deb'; Sha='e40d29f1d1a62393bacaedebe0da3d9006084152a9f7e5e029293f08ce1c5c80' },
    @{ File='pool/main/libx/libxcb/libxcb1-dev_1.15-1ubuntu2_amd64.deb'; Sha='1bafe3432feafc9e57f858721da524dfb3ee1f6fc4ef6b0c73023e79eadb9c28' },
    @{ File='pool/main/libx/libxcb/libxcb1_1.15-1ubuntu2_amd64.deb'; Sha='e1c6611d11ad7398326f1bf028afc34c3b14c51d917a3426b966ed4b9687fa58' },
    @{ File='pool/main/libx/libxdmcp/libxdmcp-dev_1.1.3-0ubuntu6_amd64.deb'; Sha='ba2f2230b391c292f5f399d6e04d48181be6b3100556f7870591ca95c9d00667' },
    @{ File='pool/main/libx/libxdmcp/libxdmcp6_1.1.3-0ubuntu6_amd64.deb'; Sha='bcd336fce11ce2a45f34d0f95e6980af22529f22147e8f98c156e5cee8ee42bb' },
    @{ File='pool/main/x/xorgproto/x11proto-dev_2023.2-1_all.deb'; Sha='3bf13a2ffb79ecd4014d438936014e5863f79eb59ebf76e8f43356271c90e7ce' },
    @{ File='pool/main/x/xtrans/xtrans-dev_1.4.0-1_all.deb'; Sha='45277c51d5d83db351b61859314b59595c9626ac372fbb2fc0d5542e169d9086' }
)

$cache = Join-Path $PSScriptRoot '..\.android-cache\ubuntu-gui-debs'
$extract = Join-Path $PSScriptRoot '..\.android-cache\ubuntu-gui-extract'
New-Item -ItemType Directory -Force $cache | Out-Null
if (Test-Path $Output) { Remove-Item -LiteralPath $Output -Recurse -Force }
if (Test-Path $extract) { Remove-Item -LiteralPath $extract -Recurse -Force }
New-Item -ItemType Directory -Force $Output, $extract | Out-Null

$links = [Collections.Generic.List[object]]::new()
foreach ($package in $packages) {
    $name = [IO.Path]::GetFileName($package.File)
    $deb = Join-Path $cache $name
    if (-not (Test-Path $deb) -or (Get-FileHash $deb -Algorithm SHA256).Hash.ToLowerInvariant() -ne $package.Sha) {
        Write-Host "Lade Linux-GUI-Sysroot: $name"
        Invoke-WebRequest ('https://archive.ubuntu.com/ubuntu/' + $package.File) -OutFile $deb -UseBasicParsing
    }
    $actual = (Get-FileHash $deb -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($actual -ne $package.Sha) { throw "SHA-256 stimmt nicht für $name" }
    $packageDir = Join-Path $extract ([IO.Path]::GetFileNameWithoutExtension($name))
    New-Item -ItemType Directory -Force $packageDir | Out-Null
    & bsdtar -xf $deb -C $packageDir
    if ($LASTEXITCODE -ne 0) { throw "DEB kann nicht geöffnet werden: $name" }
    $data = Get-ChildItem $packageDir -File | Where-Object Name -Like 'data.tar*' | Select-Object -First 1
    if (-not $data) { throw "Datenarchiv fehlt in $name" }
    $excludeArgs = [Collections.Generic.List[string]]::new()
    foreach ($line in (& bsdtar -tvf $data.FullName)) {
        if ($line -notmatch '\s(\./\S+) -> (\S+)$') { continue }
        $archivePath = $matches[1]
        $relative = $archivePath.Substring(2).Replace('/', '\')
        $destination = Join-Path $Output $relative
        $target = $matches[2]
        $source = if ($target.StartsWith('/')) {
            Join-Path $Output $target.TrimStart('/').Replace('/', '\')
        } else {
            Join-Path (Split-Path -Parent $destination) $target.Replace('/', '\')
        }
        $links.Add([pscustomobject]@{ Destination=$destination; Source=$source; Relative=$relative; Target=$target })
        $excludeArgs.Add("--exclude=$archivePath")
    }
    $tarArgs = @('-xf', $data.FullName, '-C', $Output) + $excludeArgs.ToArray()
    & bsdtar @tarArgs
    if ($LASTEXITCODE -ne 0) { throw "Paketinhalt kann nicht entpackt werden: $name" }
}

for ($pass = 0; $pass -lt 8 -and $links.Count -gt 0; $pass++) {
    $pending = [Collections.Generic.List[object]]::new()
    foreach ($link in $links) {
        if (-not (Test-Path -LiteralPath $link.Source)) { $pending.Add($link); continue }
        if (Test-Path -LiteralPath $link.Destination) { Remove-Item -LiteralPath $link.Destination -Force -Recurse }
        Copy-Item -LiteralPath $link.Source -Destination $link.Destination -Force -Recurse
    }
    if ($pending.Count -eq $links.Count) { $links = $pending; break }
    $links = $pending
}
if ($links.Count -gt 0) {
    throw "Nicht auflösbare Linux-Sysroot-Verknüpfung: $($links[0].Relative) -> $($links[0].Target)"
}

Write-Host "Linux-GUI-Sysroot bereit: $Output"
