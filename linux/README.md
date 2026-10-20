# KR Compiler IDE v4.3.1 – Linux x64

Dieser Quellbaum verwendet dieselbe KR-Unified-Frontendpipeline und dieselbe
Adaptive-Compute-Analyse wie Android und Windows. Die portable Distribution
erwartet ausschließlich eingebettete Komponenten unter `toolchain/`; es ist
kein Download während Build oder Programmausführung vorgesehen.

## Verbindliches Bundle-Layout

- `toolchain/jdk/bin/javac`, `toolchain/jdk/bin/jar`: IDE-Build
- `toolchain/jdk/bin/java`: IDE-Laufzeit (dieselbe portable Laufzeit wie beim Build)
- `toolchain/bin/clang{,++}`, `cmake`, `ninja`: Linux-x64-Compiler
- `toolchain/rust/bin/{rustc,cargo}` und `toolchain/go/bin/go`
- `toolchain/targets/windows/bin/x86_64-w64-mingw32-*`: Windows-Crossziel
- `toolchain/targets/windows/bin/SDL3.dll`: Windows-SDL3-Laufzeit für
  Crossbuild-Ausgaben
- `toolchain/android/ndk`: Android-ARM64-Crossziel
- `components/cc-opencl`: quellbasierte CC_OpenCL-Enterprise-Komponente
- `toolchain/targets/linux/sysroot/usr/include/SDL3`: SDL-3.4.18-Header
- `toolchain/targets/linux/sysroot/usr/lib/x86_64-linux-gnu/libSDL3.so*`:
  portable SDL3-Laufzeit und Linkbibliothek

`build.sh` bricht absichtlich ab, wenn das portable JDK fehlt. Es fällt nicht
stillschweigend auf ein System-SDK oder einen Netzwerkdownload zurück.

Der Binärbestand wird einmalig für die Distribution mit
`scripts/provision-toolchain.sh` aus fest versionierten und geprüften
Herstellerpaketen erzeugt. Die fertige IDE lädt bei Benutzung nichts nach.
GPU-Treiber werden aus Lizenz- und Hardwaregründen nicht mitgeliefert; der
eingebettete OpenCL-ICD-Loader verwendet den bereits installierten Treiber.

SDL3-Projekte deklarieren `packages = ["sdl3"]` in der `.krproject`. Die IDE
verlinkt SDL3 automatisch und kopiert `libSDL3.so.0` neben das erzeugte
Programm. Auf dem Zielsystem sind deshalb keine SDL-Entwicklungspakete nötig.
