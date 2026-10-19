# KR Compiler IDE v4.2.0 – Linux x64

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
- `toolchain/android/ndk`: Android-ARM64-Crossziel
- `components/cc-opencl`: quellbasierte CC_OpenCL-Enterprise-Komponente

`build.sh` bricht absichtlich ab, wenn das portable JDK fehlt. Es fällt nicht
stillschweigend auf ein System-SDK oder einen Netzwerkdownload zurück.

Der Binärbestand wird einmalig für die Distribution mit
`scripts/provision-toolchain.sh` aus fest versionierten und geprüften
Herstellerpaketen erzeugt. Die fertige IDE lädt bei Benutzung nichts nach.
GPU-Treiber werden aus Lizenz- und Hardwaregründen nicht mitgeliefert; der
eingebettete OpenCL-ICD-Loader verwendet den bereits installierten Treiber.
