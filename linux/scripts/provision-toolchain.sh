#!/usr/bin/env bash
set -euo pipefail

# Builds the distributable Linux-x64 toolchain. Run inside Ubuntu/WSL, never at
# IDE runtime. Every network artifact is pinned and verified before extraction.
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
LINUX_ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
REPO_ROOT=$(CDPATH= cd -- "$LINUX_ROOT/.." && pwd)
DEST="$LINUX_ROOT/toolchain"
CACHE="$REPO_ROOT/.linux-toolchain-cache"
STAGE="$REPO_ROOT/github-release/.android-cache/toolchain-stage/targets"

case "$DEST" in "$REPO_ROOT"/linux/toolchain) ;; *) echo "Unsafe destination: $DEST" >&2; exit 2;; esac
test -d "$STAGE/linux/sysroot" || { echo "Linux target sysroot missing: $STAGE" >&2; exit 2; }
test -d "$STAGE/windows" || { echo "Windows target sysroot missing: $STAGE" >&2; exit 2; }
mkdir -p "$CACHE/downloads" "$CACHE/work"
# C: can be completely full on the build host. Force every cache and temporary
# location used by download/install helpers into the project on D:.
mkdir -p "$CACHE/home" "$CACHE/tmp" "$CACHE/xdg-cache" "$CACHE/xdg-config"
export HOME="$CACHE/home"
export TMPDIR="$CACHE/tmp"
export TMP="$CACHE/tmp"
export TEMP="$CACHE/tmp"
export XDG_CACHE_HOME="$CACHE/xdg-cache"
export XDG_CONFIG_HOME="$CACHE/xdg-config"
export GOCACHE="$CACHE/go-build-cache"
export GOMODCACHE="$CACHE/go-mod-cache"

download_sha256() {
  local name=$1 url=$2 expected=$3 file
  file="$CACHE/downloads/$name"
  if [ ! -f "$file" ] || [ "$(sha256sum "$file" | awk '{print $1}')" != "$expected" ]; then
    rm -f "$file.part"
    curl --fail --location --retry 5 --output "$file.part" "$url"
    echo "$expected  $file.part" | sha256sum --check --status
    mv "$file.part" "$file"
  fi
  printf '%s\n' "$file"
}

download_sha1() {
  local name=$1 url=$2 expected=$3 file
  file="$CACHE/downloads/$name"
  if [ ! -f "$file" ] || [ "$(sha1sum "$file" | awk '{print $1}')" != "$expected" ]; then
    rm -f "$file.part"
    curl --fail --location --retry 5 --output "$file.part" "$url"
    echo "$expected  $file.part" | sha1sum --check --status
    mv "$file.part" "$file"
  fi
  printf '%s\n' "$file"
}

LLVM=$(download_sha256 LLVM-21.1.8-Linux-X64.tar.xz \
  https://github.com/llvm/llvm-project/releases/download/llvmorg-21.1.8/LLVM-21.1.8-Linux-X64.tar.xz \
  b3b7f2801d15d50736acea3c73982994d025b01c2f035b91ae3b49d1b575732b)
JDK=$(download_sha256 OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz \
  'https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz' \
  ce79869e1307ed8ee1e2baa86a412b1eb5b75d10a01006d788a6f968bcfaee94)
CMAKE=$(download_sha256 cmake-4.4.4-linux-x86_64.tar.gz \
  https://github.com/Kitware/CMake/releases/download/v4.4.4/cmake-4.4.4-linux-x86_64.tar.gz \
  e5bb807f7728cb60cd8b27ebc97a2edb469b68655f21e844a600c3575b76f5bb)
NINJA=$(download_sha256 ninja-linux.zip \
  https://github.com/ninja-build/ninja/releases/download/v1.13.1/ninja-linux.zip \
  0830252db77884957a1a4b87b05a1e2d9b5f658b8367f82999a941884cbe0238)
GO=$(download_sha256 go1.27.1.linux-amd64.tar.gz \
  https://go.dev/dl/go1.27.1.linux-amd64.tar.gz \
  63d339f0da5ab53635a56f2490a7984dfe12dfcff22ad749f63edaf590168445)
RUSTUP=$(download_sha256 rustup-init-1.28.2-x86_64-unknown-linux-gnu \
  https://static.rust-lang.org/rustup/archive/1.28.2/x86_64-unknown-linux-gnu/rustup-init \
  20a06e644b0d9bd2fbdbfd52d42540bdde820ea7df86e92e533c073da0cdd43c)
NDK=$(download_sha1 android-ndk-r30-linux.zip \
  https://dl.google.com/android/repository/android-ndk-r30-linux.zip \
  5107f898313790e449e87eee2183d9a20602dee9)

LLVM_DIR=LLVM-21.1.8-Linux-X64
NEW="$CACHE/work/new-toolchain"
if [ ! -x "$NEW/bin/clang" ]; then
  rm -rf "$NEW"
  if [ ! -x "$CACHE/work/$LLVM_DIR/bin/clang" ]; then
    rm -rf "$CACHE/work/$LLVM_DIR"
    tar -xJf "$LLVM" -C "$CACHE/work"
  fi
  # Rename within the D: project cache instead of copying the multi-gigabyte tree.
  mv "$CACHE/work/$LLVM_DIR" "$NEW"
fi
mkdir -p "$NEW/bin" "$NEW/android" "$NEW/targets"

mkdir -p "$NEW/jdk"
tar -xzf "$JDK" -C "$NEW/jdk" --strip-components=1
mkdir -p "$NEW/cmake"
tar -xzf "$CMAKE" -C "$NEW/cmake" --strip-components=1
for tool in cmake cpack ctest; do cp -a "$NEW/cmake/bin/$tool" "$NEW/bin/$tool"; done
cp -a "$NEW/cmake/share" "$NEW/"
(cd "$NEW/bin" && "$NEW/jdk/bin/jar" xf "$NINJA")
chmod +x "$NEW/bin/ninja"

tar -xzf "$GO" -C "$NEW"
python3 "$SCRIPT_DIR/extract-zip.py" "$NDK" "$NEW/android"
mv "$NEW/android/android-ndk-r30" "$NEW/android/ndk"
cp -a "$STAGE/linux" "$NEW/targets/linux"
cp -a "$STAGE/windows" "$NEW/targets/windows"

# Install a pinned minimal Rust toolchain into the bundle, including both cross targets.
chmod +x "$RUSTUP"
RUSTUP_HOME="$NEW/rustup-home" CARGO_HOME="$NEW/cargo-home" \
  "$RUSTUP" -y --no-modify-path --profile minimal --default-toolchain 1.99.1 \
  --target x86_64-pc-windows-gnu,aarch64-linux-android
for tool in cargo rustc rustdoc rustfmt; do
  cat >"$NEW/bin/$tool" <<EOF
#!/usr/bin/env sh
ROOT=\$(CDPATH= cd -- "\$(dirname -- "\$0")/.." && pwd)
export RUSTUP_HOME="\$ROOT/rustup-home" CARGO_HOME="\$ROOT/cargo-home"
exec "\$ROOT/cargo-home/bin/$tool" "\$@"
EOF
  chmod +x "$NEW/bin/$tool"
done

# Clang wrappers make the embedded sysroots mandatory and keep host SDKs out.
mv "$NEW/bin/clang" "$NEW/bin/clang.real"
mv "$NEW/bin/clang++" "$NEW/bin/clang++.real"
cat >"$NEW/bin/clang" <<'EOF'
#!/usr/bin/env sh
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec "$ROOT/bin/clang.real" -fuse-ld=lld --target=x86_64-linux-gnu --sysroot="$ROOT/targets/linux/sysroot" --gcc-toolchain="$ROOT/targets/linux/gcc" "$@"
EOF
cat >"$NEW/bin/clang++" <<'EOF'
#!/usr/bin/env sh
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
exec "$ROOT/bin/clang.real" --driver-mode=g++ -fuse-ld=lld --target=x86_64-linux-gnu --sysroot="$ROOT/targets/linux/sysroot" --gcc-toolchain="$ROOT/targets/linux/gcc" "$@"
EOF
mkdir -p "$NEW/targets/windows/bin"
cat >"$NEW/targets/windows/bin/x86_64-w64-mingw32-clang" <<'EOF'
#!/usr/bin/env sh
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
exec "$ROOT/bin/clang.real" -fuse-ld=lld --target=x86_64-w64-windows-gnu --sysroot="$ROOT/targets/windows" "$@"
EOF
cat >"$NEW/targets/windows/bin/x86_64-w64-mingw32-clang++" <<'EOF'
#!/usr/bin/env sh
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
exec "$ROOT/bin/clang.real" --driver-mode=g++ -fuse-ld=lld \
  --target=x86_64-w64-windows-gnu --sysroot="$ROOT/targets/windows" \
  -resource-dir="$ROOT/targets/windows/lib/clang/23" --rtlib=compiler-rt \
  -stdlib=libc++ -isystem "$ROOT/targets/windows/include/c++/v1" \
  -isystem "$ROOT/targets/windows/include" \
  -L"$ROOT/targets/windows/x86_64-w64-mingw32/lib" "$@" -lunwind
EOF
chmod +x "$NEW/bin/clang" "$NEW/bin/clang++" "$NEW/targets/windows/bin/"*

# Ubuntu's redistributable ICD loader is bundled for link/runtime; vendor GPU
# implementations remain the responsibility of the installed graphics driver.
mkdir -p "$CACHE/work/opencl-debs" "$CACHE/work/opencl-root"
(cd "$CACHE/work/opencl-debs" && apt-get download \
  ocl-icd-libopencl1 ocl-icd-opencl-dev gcc-14-base libgcc-s1 libstdc++6 >/dev/null)
rm -rf "$CACHE/work/opencl-root" && mkdir -p "$CACHE/work/opencl-root"
for deb in "$CACHE/work/opencl-debs"/*.deb; do dpkg-deb -x "$deb" "$CACHE/work/opencl-root"; done
cp -a "$CACHE/work/opencl-root/usr/lib/x86_64-linux-gnu/"libOpenCL.so* "$NEW/targets/linux/sysroot/usr/lib/x86_64-linux-gnu/"
cp -a "$CACHE/work/opencl-root/usr/lib/x86_64-linux-gnu/libgcc_s.so.1" "$NEW/targets/linux/sysroot/usr/lib/x86_64-linux-gnu/"
cp -a "$CACHE/work/opencl-root/usr/lib/x86_64-linux-gnu/"libstdc++.so.6* "$NEW/targets/linux/sysroot/usr/lib/x86_64-linux-gnu/"
mkdir -p "$NEW/licenses/ubuntu"
cp -a "$CACHE/work/opencl-root/usr/share/doc/ocl-icd-libopencl1" "$NEW/licenses/ubuntu/"
cp -a "$CACHE/work/opencl-root/usr/share/doc/libgcc-s1" "$NEW/licenses/ubuntu/"
cp -a "$CACHE/work/opencl-root/usr/share/doc/libstdc++6" "$NEW/licenses/ubuntu/"
cp -a "$CACHE/work/opencl-root/usr/share/doc/gcc-14-base" "$NEW/licenses/ubuntu/"

cat >"$NEW/manifest.txt" <<EOF
KR Compiler IDE portable Linux-x64 toolchain
LLVM 21.1.8 (Apache-2.0 WITH LLVM-exception)
Eclipse Temurin JDK 21.0.12+1 (GPL-2.0 WITH Classpath-exception)
CMake 4.4.4 (BSD-3-Clause)
Ninja 1.13.1 (Apache-2.0)
Go 1.27.1 (BSD-3-Clause)
Rust 1.99.1 / rustup 1.28.2 (MIT OR Apache-2.0)
Android NDK r30 (Android SDK licenses)
OpenCL ICD Loader: Ubuntu ocl-icd package (BSD-2-Clause)
Target sysroots: existing KR verified cache
EOF
for deb in "$CACHE/work/opencl-debs"/*.deb; do
  printf 'Ubuntu package %s %s sha256=%s\n' \
    "$(dpkg-deb -f "$deb" Package)" "$(dpkg-deb -f "$deb" Version)" \
    "$(sha256sum "$deb" | awk '{print $1}')" >>"$NEW/manifest.txt"
done

rm -rf "$DEST"
mv "$NEW" "$DEST"
echo "Portable Linux-x64 toolchain installed at $DEST"
"$DEST/bin/clang++" --version | head -n1
"$DEST/jdk/bin/java" -version
"$DEST/bin/cmake" --version | head -n1
"$DEST/bin/cargo" --version
"$DEST/go/bin/go" version
