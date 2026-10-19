#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVAC="$ROOT/toolchain/jdk/bin/javac"
JAR="$ROOT/toolchain/jdk/bin/jar"
if [ ! -x "$JAVAC" ] || [ ! -x "$JAR" ]; then
  echo "Portable KR JDK fehlt: linux/toolchain/jdk (Linux x64)." >&2
  exit 2
fi
mkdir -p "$ROOT/bin" "$ROOT/dist/components/cc-opencl"
find "$ROOT/src" -name '*.java' -print0 | xargs -0 "$JAVAC" -encoding UTF-8 -d "$ROOT/bin"
"$JAR" cfm "$ROOT/dist/KR-Compiler-IDE.jar" "$ROOT/Manifest.txt" -C "$ROOT/bin" .
cp -R "$ROOT/components/cc-opencl/." "$ROOT/dist/components/cc-opencl/"
echo "Erzeugt: $ROOT/dist/KR-Compiler-IDE.jar"
