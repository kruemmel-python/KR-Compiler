# KR Compiler Android IDE v4.0.3 – Release Notes

Version 4.0.3 erweitert die KR Compiler Android IDE um **vollständigen Support für modulare Großprojekte (`*.krproject`)** und die Multi-File KR-Architektur. Damit können komplexe Enterprise-, Forschungs- und Simulationssysteme (wie `KR_FORGE`) mit hierarchischer Verzeichnisstruktur, modularen Übersetzungseinheiten und lückenlosem Cross-Language FFI nativ auf Android für alle drei Zielplattformen (**Android ARM64**, **Windows x64**, **Linux x64**) gebaut werden.

Zudem wurden die Versionsanzeige und der Infobereich vereinheitlicht und modernisiert.

---

## Neuerungen in Version 4.0.3

### 1. Modulare Multi-File KR-Architektur (`*.krproject`)
- **Deklarative Projektkonfiguration**: Neue TOML-basierte Konfigurationsdatei `*.krproject` mit Sektionen für Metadaten (`[project]`), rekursive Scan-Ordner (`[modules]`), Drittpakete (`[dependencies]`) und Compiler-Flags (`[build]`).
- **Hierarchische Verzeichnisstrukturen**: Volle Unterstützung für Enterprise-Strukturen am Referenzmodell `KR_FORGE` (`app/`, `gui/`, `research/`, `compute/`, `orchestration/`, `storage/`, etc.).
- **Automatischer Modul-Scan & Pfadnormalisierung**: `KrPipeline` erkennt alle deklarierten Unterordner, relativiert Quellpfade gegen das Projektverzeichnis und verhindert Namenskollisionen.
- **Inkrementelle Translation Units**: `KrCodeGenerator` zerlegt modulare `.kr`-Dateien in getrennte C++ Translation Units unter `modules/<modul_name>_kr.cpp`.
- **Konsolidierte Rust- und Go-Module**: Modulübergreifende Konsolidierung von Rust-Funktionen in `kr_rust/src/lib.rs` und Go-Routinen in `kr_go/main.go`.
- **Zentraler ABI-Header `kr_bridge.h`**: Automatische Prototypen-Generierung für alle Funktionen über Modul- und Sprachgrenzen hinweg.

---

### 2. Multi-Dependency Management mit vcpkg
- Automatische Erzeugung von kanonischen `vcpkg.json`-Manifesten direkt aus der `[dependencies]`-Sektion von `*.krproject`.
- Nahtlose Verknüpfung externer C/C++-Bibliotheken (z. B. `opencl`, `sqlite3`, `nlohmann-json`) mit der integrierten Toolchain auf allen drei Zielsystemen.

---

### 3. UI- & Versions-Harmonisierung
- **Menüeintrag vereinfacht**: Der Menüpunkt lautet nun prägnant **„Über KR IDE“** (ohne statisch veraltende Versionsanhängsel).
- **Entwickler-Würdigung**: Im Infodialog wird **Entwickler: Ralf Krümmel** prominent aufgeführt.
- **Dynamische Versionsauflösung**: `MainActivity` ermittelt die aktive Versionsnummer (`v4.0.3`) nun dynamisch via `PackageManager` / `getAppVersionName()`.

---

### 4. Physische Verifikation auf Testgerät
- Getestet auf **Samsung Galaxy Tab S6 Lite (SM-P615, Android 13, ARM64-v8a)**:
  - **Android ARM64**: `programm-android-arm64.apk` (2.8 MB) + `libuser.so` (2.3 MB) – **PASS**
  - **Windows x64**: `programm.exe` (8.5 MB) – **PASS**
  - **Linux x64**: `programm` (7.9 MB) – **PASS**
- Instrumentation Test Runner: `result=3 Sprach-Builds erfolgreich` (Exit Code `-1`).

---

### 5. Volle Abwärtskompatibilität
- Alle 18 realen C/C++ GitHub-Repositories (54/54 Builds) bleiben voll funktionsfähig.
- Single-File `.kr`-Projekte, native Rust- und Go-Projekte laufen unverändert weiter.

---

## Prüfsumme

- **Datei**: `KR-Compiler-Android-IDE-v4.0.3.apk`
- **Größe**: `744.016.332 Bytes`
- **SHA-256**: `82841D86BE43A3DE0DFFEEC3F9D33BED55ABE1F0209DB1FCFF7D35B348C24125`
- **Signatur**: Release-Entwicklungsschlüssel (v4.0-kompatibel)
