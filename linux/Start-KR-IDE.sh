#!/usr/bin/env sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
JAVA="$ROOT/toolchain/jdk/bin/java"
if [ ! -x "$JAVA" ]; then
  echo "Portable KR Java-Laufzeit fehlt: $ROOT/toolchain/jdk" >&2
  exit 2
fi
exec "$JAVA" -jar "$ROOT/dist/KR-Compiler-IDE.jar" "$@"
