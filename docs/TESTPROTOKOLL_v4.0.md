# KR Compiler Android IDE v4.0 Testprotokoll

Testgerät war ein verbundenes RMX3853. Die Test-App legte vier neue Projekte an und baute jedes Projekt für Android ARM64, Windows x64 und Linux x64. Ergebnis: zwölf von zwölf Builds erfolgreich.

| Projekt | Android APK | Windows EXE | Linux ELF |
|---|---:|---:|---:|
| Rust Konsole | 824.189 Byte | 573.440 Byte | 412.520 Byte |
| Rust GUI | 1.622.984 Byte | 4.548.096 Byte | 3.664.904 Byte |
| Go Konsole | 1.987.453 Byte | 3.379.200 Byte | 3.312.800 Byte |
| Go GUI | 2.601.928 Byte | 6.817.280 Byte | 5.918.848 Byte |

## Laufzeitprüfungen

- Alle vier Android-APKs wurden auf dem RMX3853 installiert und gestartet.
- Rust- und Go-Konsolenprogramme gaben den erwarteten Text aus und endeten mit Exitcode 0.
- Beide Windows-Konsolenprogramme liefen nativ und gaben den erwarteten Text aus.
- Beide Linux-Konsolenprogramme liefen unter Ubuntu und gaben den erwarteten Text aus.
- Rust- und Go-GUI für Windows öffneten jeweils ein SDL3-Fenster.
- Die Linux-GUI-Programme wurden als x86-64-ELF geprüft und ohne sofortigen Absturz gestartet.
- Alle Windows-Dateien wurden als `COFF-x86-64`, alle Linux-Dateien als `elf64-x86-64` erkannt.

## Release APK

- Datei: `KR-Compiler-Android-IDE-v4.0.0.apk`
- SHA-256: `EE4E841C0D9A6D533DE32C6A47B8B56045CB974FC5A888D4638F6616517C4A5D`
- Signatur: vorhandener KR-Entwicklungsschlüssel, damit die installierte App aktualisiert werden kann

Die zwölf Beispiel-Artefakte werden im GitHub-Release zusätzlich als ZIP-Paket bereitgestellt.
