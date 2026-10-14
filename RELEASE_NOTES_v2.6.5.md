# KR Compiler Android v2.6.5

Diese Version enthält den vollständigen mobilen C/C++-Compiler für Android-ARM64-Geräte.

## Enthalten

- CMake 4.4, Ninja, Git, pkg-config und Clang/LLVM 21.1.8
- Zielumgebungen für Android ARM64, Windows x64 und Linux x64
- CMake-Auswertung einschließlich `FetchContent`
- optionaler, geprüfter Online-Download bekannter CMake-Abhängigkeiten
- signierte Android-Runner-APKs mit eingebetteten Projektressourcen
- relative Ressourcenpfade funktionieren im entpackten Projekt-Arbeitsverzeichnis
- vollständiger öffentlicher Quellcode und automatischer Fork-Build

## Release-Dateien

- `KR-Compiler-Android-v2.6.5.apk`: installierbare Compiler-App
- `KR_Compiler_Android_Dokumentation_v2.6.5_14-10-2026.docx`: vollständige Dokumentation
- `Mycelia-CSS-Android-arm64-v2.6.5.apk`: gebautes Beispiel mit Ressourcenkatalog
- `KR-Compiler-Android-toolchain-v2.6.5.zip`: Laufzeit für Builds aus einem Fork

## SHA-256

- Compiler-APK: `09ec185d684e140d6d67c60e1e2217c1edcc76ebb943cd368f57d60641096a21`
- Compiler-Laufzeit: `36a62d8f8a20de53b217543cf5438f23000478ac97d6ed161a450f677d2cba5c`
- Dokumentation: `9d6fca28051ae2cb151900c1af2220429a0224a81d9f1969dcb078ded44e53e5`
- Mycelia-Beispiel: `562e5cacffb338b6572e95cd3ecc4d3ee6379972c2a7d20d7f48ef8f67be7f0d`

Die offizielle APK ist mit dem KR-Compiler-Release-Schlüssel signiert. Forks erzeugen beim
ersten lokalen Build automatisch einen eigenen Entwicklungsschlüssel.
