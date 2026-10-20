#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
ROOT=$(CDPATH= cd -- "$SCRIPT_DIR/.." && pwd)
TC="$ROOT/toolchain"
OUT="$ROOT/.verification"
SMOKE="$ROOT/tests/smoke"
rm -rf "$OUT"
mkdir -p "$OUT"

export HOME="$OUT/home" TMPDIR="$OUT/tmp" TMP="$OUT/tmp" TEMP="$OUT/tmp"
export XDG_CACHE_HOME="$OUT/xdg-cache" GOCACHE="$OUT/go-cache" GOMODCACHE="$OUT/go-mod-cache"
mkdir -p "$HOME" "$TMPDIR" "$XDG_CACHE_HOME" "$GOCACHE" "$GOMODCACHE"

"$TC/jdk/bin/javac" -version
"$TC/bin/cmake" --version | head -n1
"$TC/bin/ninja" --version

"$TC/bin/clang++" -std=c++20 "$SMOKE/hello.cpp" -o "$OUT/hello-linux"
"$OUT/hello-linux" | grep -F KR-LINUX-PORTABLE-OK

test -f "$TC/targets/linux/sysroot/usr/include/SDL3/SDL.h"
SDL_LIB="$TC/targets/linux/sysroot/usr/lib/x86_64-linux-gnu"
if [ ! -d "$SDL_LIB" ]; then SDL_LIB="$TC/targets/linux/sysroot/usr/lib"; fi
test -f "$SDL_LIB/libSDL3.so"
"$TC/bin/clang++" -std=c++20 "$SMOKE/sdl3_gui.cpp" \
  -L"$SDL_LIB" -Wl,-rpath,'$ORIGIN' -lSDL3 -o "$OUT/sdl3-gui-linux"
cp -f "$SDL_LIB/libSDL3.so.0" "$OUT/libSDL3.so.0"
SDL_VIDEODRIVER=dummy LD_LIBRARY_PATH="$OUT" "$OUT/sdl3-gui-linux" | grep -F KR-SDL3-GUI-OK

"$TC/targets/windows/bin/x86_64-w64-mingw32-clang++" -std=c++20 "$SMOKE/hello.cpp" -o "$OUT/hello-windows.exe"
file "$OUT/hello-windows.exe" | grep -F 'PE32+'
test -f "$TC/targets/windows/include/SDL3/SDL.h"
test -f "$TC/targets/windows/x86_64-w64-mingw32/lib/libSDL3.dll.a"
test -f "$TC/targets/windows/bin/SDL3.dll"
"$TC/targets/windows/bin/x86_64-w64-mingw32-clang++" -std=c++20 \
  "$SMOKE/sdl3_gui.cpp" -lSDL3 -o "$OUT/sdl3-gui-windows.exe"
file "$OUT/sdl3-gui-windows.exe" | grep -F 'PE32+'

"$TC/android/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android26-clang++" \
  -std=c++20 -shared -fPIC "$SMOKE/hello.cpp" -o "$OUT/libhello-android.so"
file "$OUT/libhello-android.so" | grep -F 'ARM aarch64'

"$TC/bin/rustc" "$SMOKE/hello.rs" -o "$OUT/hello-rust"
"$OUT/hello-rust" | grep -F KR-RUST-PORTABLE-OK

"$TC/go/bin/go" build -o "$OUT/hello-go" "$SMOKE/hello.go"
"$OUT/hello-go" | grep -F KR-GO-PORTABLE-OK

CC="$ROOT/components/cc-opencl"
"$TC/bin/clang++" -std=c++20 -O2 -fPIC -shared -Wall -Wextra -Werror \
  -DCL_TARGET_OPENCL_VERSION=300 -DCL_USE_DEPRECATED_OPENCL_1_2_APIS \
  -I"$CC" -I"$CC/include" -I"$CC/CL" -x c++ \
  "$CC/src/CC_OpenCL.c" "$CC/src/CipherCore_NoiseCtrl.c" \
  -L"$TC/targets/linux/sysroot/usr/lib/x86_64-linux-gnu" -lOpenCL \
  -o "$OUT/libCC_OpenCL.so"
"$TC/bin/clang++" -std=c++20 "$SMOKE/cc_opencl_probe.cpp" \
  -L"$OUT" -lCC_OpenCL -Wl,-rpath,'$ORIGIN' -o "$OUT/cc-opencl-probe"
LD_LIBRARY_PATH="$OUT:$TC/targets/linux/sysroot/usr/lib/x86_64-linux-gnu" \
  "$OUT/cc-opencl-probe" | grep -E 'KR-CC-OPENCL-(GPU-OK|NO-DEVICE)'

"$ROOT/build.sh"
test -s "$ROOT/dist/KR-Compiler-IDE.jar"
echo 'All portable toolchain smoke tests passed.'
