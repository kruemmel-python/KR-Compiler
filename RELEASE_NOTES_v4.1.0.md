# KR Compiler IDE v4.1.0

Version 4.1.0 ergänzt Adaptive Compute, bindet den tatsächlichen
CC_OpenCL-Enterprise-Quellbestand ein und liefert erstmals einen eigenständigen
portablen Linux-x64-IDE-Quellbaum samt Offline-Toolchain.

## Wesentliche Änderungen

- Strukturierte Analyse unabhängiger elementweiser Schleifen und auditierter
  `adaptive_compute_plan.json`.
- Deterministischer Dispatcher für `AUTO`, `CPU_ONLY`, `GPU_ONLY` und `VERIFY`.
- Konservative Sicherheitsprüfungen für Abhängigkeiten, Seiteneffekte,
  Aliasing, Datentypen, Speicherbedarf und Hardwarefähigkeiten.
- CC_OpenCL Enterprise als quellbasierte Komponente für Android, Windows und
  Linux; keine proprietären GPU-Treiber werden mitgeliefert.
- Android-Buildprüfung für exportierte `main`, `SDL_main` oder
  `KrLanguageMain`-Einstiegspunkte.
- Portable Linux-x64-IDE mit LLVM 21.1.8, Temurin 21, CMake 4.4.4,
  Ninja 1.13.1, Go 1.27.1, Rust 1.99.1 und Android NDK r30.
- Portable Windows-Java-Laufzeit; IDE-Starter laden beim Start nichts nach.

## Verifikation

- Android `testDebugUnitTest` und APK-Build: erfolgreich.
- Linux C++20, Rust und Go: kompiliert und ausgeführt.
- Linux-Crossbuild nach Windows x64: gültiges PE32+ erzeugt.
- Linux-Crossbuild nach Android ARM64: gültiges AArch64-ELF erzeugt.
- Linux CC_OpenCL: warning-clean gebaut; fehlende OpenCL-Plattform wird ohne
  Absturz und mit eindeutiger Diagnose behandelt.
- Windows CC_OpenCL auf AMD OpenCL: 50/50 Kernels kompiliert und 60/60
  numerische Validierungen bestanden.

## Downloads

- `KR-Compiler-Android-IDE-v4.1.0.apk`: portable Android-IDE einschließlich
  Compilerbestand.
- `KR-Compiler-Windows-x64-v4.1.0.zip`: vollständige portable Windows-IDE mit
  Java-Laufzeit und Compiler-Toolchains.
- `KR-Compiler-Linux-x64-v4.1.0.tar.xz`: vollständige portable Linux-x64-IDE
  mit Java-Laufzeit und Compiler-Toolchains.
- `KR-Compiler-Windows-toolchain-v4.1.0.zip`: separates Bootstrap-/Recovery-
  Paket für den Windows-Quellbaum.
- `SHA256SUMS-v4.1.0.txt`: SHA-256-Prüfsummen aller Binärpakete.

## Ehrlicher Projektstatus

Der umfassende Enterprise-Implementierungsauftrag ist weiterhin
**INCOMPLETE**. Noch offen sind unter anderem vollständige automatische native
Codegenerierung für alle geforderten Operationsfamilien, die komplette
Live-Telemetrie-Oberfläche, physische Android-Adreno/Mali-E2E-Tests und die
vollständige Benchmark-/Energie- und Regressionsmatrix. Siehe
`docs/IMPLEMENTATION_STATUS.md`.
