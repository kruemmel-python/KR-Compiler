# KR Compiler Android IDE

KR Compiler Android IDE ist eine autarke, native mobile Entwicklungsumgebung für **KR Unified Language (`.kr`)**, **C++20**, **Rust** und **Go** auf ARM64-Android-Geräten. Projekte werden direkt auf dem Smartphone erstellt, analysiert und für **Android ARM64**, **Windows x64** oder **Linux x64** gebaut. Sowohl reine Konsolenprogramme als auch hardwarebeschleunigte grafische **SDL3-Anwendungen** werden nativ unterstützt.

---

## Version 4.0.3 herunterladen

Die installierbare APK, die bebilderten Benutzerhandbücher, die technische Dokumentation und die geprüften Beispiel-Artefakte befinden sich im [GitHub-Release v4.0.3](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.0.3).

- **Release-APK (Android)**: `KR-Compiler-Android-IDE-v4.0.3.apk`
- **SHA-256 (APK)**: `ac7823db51e65b920d1445ea5ab6db8883d2fd692f4110412f1c61a1be35094d`
- **Windows Desktop Edition**: `KR-Compiler-IDE.jar` (1:1 Port für Windows 10/11, Starter: `windows/Start-KR-IDE.bat`)
- **SHA-256 (Windows JAR)**: `059b9a8550f6813654b60b6b8a41519f362b8952f7248c3179bb46841dcb2c1b` (188.369 Bytes)
- **Windows Toolchain (Portabel)**: `KR-Compiler-Windows-toolchain-v4.0.3.zip` (Inklusive MinGW C++20, Go-Binutils, Android-APK-Runner, SDL3 & SQLite3)

### Systemvoraussetzungen
- **Android**: Android 8.0+ (API 26+), 64-Bit ARM (`arm64-v8a`), ca. 3 GiB freier Speicher
- **Windows**: Windows 10 / 11 (64-Bit), Java Runtime 17 oder neuer

---

## Neu in Version 4.0.3: Autonomer KI-Agent, Modulare Großprojekte & Windows Desktop Edition

Version 4.0.3 führt wesentliche Enterprise- und Multiplattform-Erweiterungen ein:
- **Windows Desktop Edition (Vollständige Parität zur Android-App)**:
  - **Android ARM64 APK-Erzeugung**: Baut für die Zielplattform Android nun direkt auf dem Windows-PC eine vollwertige, sofort installierbare `programm-android-arm64.apk`.
  - **Umfassende Modul-Bündelung**: Automatische Einbettung von `libuser.so` (kompiliert via Android NDK Clang), `libkr_runner.so`, `libc++_shared.so`, `libsqlite3.so`, `libSDL3.so` und `libkr_go.so`.
  - **Integrierte Signierung**: Automatische Signierung mit Android v1-, v2- und v3-Signaturen via `apksigner`.
  - **Nativer Multi-Language-Build**: Echte native Kompilierung von C++20, Rust (statische Bibliothek via Cargo für Windows x64 und Android ARM64) und Go (via portabler Go-Toolchain und MinGW-Binutils `ar`/`ranlib` als `libkr_go.a` bzw. `libkr_go.so`) ohne Fallback-Bridge.
  - **Plattformgerechte Ausgabedateien**: Windows x64 (`programm.exe`), Linux x64 ELF (`programm`) und Android ARM64 (`programm-android-arm64.apk`).
- **Integrierter autonomer KI-Programmierassistent („AGENT“)**:
  - Direkter Zugriff über den neuen **`AGENT`**-Button in der Navigationsleiste der App und der Desktop-IDE.
  - Autonome Problemlösungsschleife (ReAct): Verzeichnisse scannen (`list`), Code lesen (`read`), Code modifizieren (`write` / `replace`) und automatischen Kompilierungslauf (`build`) zur iterativen Fehlerbehebung starten.
  - **Virtuelle Quelltext-Rückführung (`KrSourceMap`)**: Automatische Zuordnung von Fehlermeldungen in Zwischenartefakten (`KR_BUILD/rust_module/src/lib.rs`, `modules/*.cpp`, `kr_bridge.h`) zurück auf die exakte `.kr`-Originaldatei und -Zeile.
  - **Präzise Verzeichnis- und Dateiauflösung**: Klare Unterscheidung zwischen Projektordnern (`app/`, `gui/`, `storage/`) und virtuellen Build-Artefakten sowie rekursive Suche via `findFileInProject()`.
  - **Intelligente Schleifenterminierung**: Erkennt einen erfolgreichen Build nach Fehlerbehebung sofort und beendet die ReAct-Schleife ohne Endlositerationen.
  - Kompatibel mit allen OpenAI-kompatiblen HTTPS-Endpunkten (OpenAI, Claude via Proxy, Mistral, Groq, OpenRouter) und lokalen Modellen (Ollama / LM Studio via `http://127.0.0.1:...`).
  - Strikte Sicherheits-Sandbox (`safeFile`), Freigabepflicht für alle Dateiänderungen („Human-in-the-Loop“) und revisionssicheres Audit-Log (`kr_agent_audit.jsonl`).
- **Modulare Projektarchitektur (`*.krproject`)**: Deklarative Konfiguration von Metadaten (`[project]`), rekursiven Modulverzeichnissen (`[modules]`), Drittpaketen (`[dependencies]`) und Compiler-Optimierungen (`[build]`).
- **Hierarchische Enterprise-Strukturen (`KR_FORGE` / `KR_NEXUS_ENTERPRISE`)**: Volle Unterstützung für Enterprise-Strukturen (`app/`, `gui/`, `research/`, `compute/`, `orchestration/`, `storage/`, `contracts/`, `tests/` und `docs/`).
- **Modulare C++ Translation Units & zentraler ABI-Header**: Automatisches Aufteilen modularer C++-Inhalte in `modules/*.cpp`, Generierung von `kr_bridge.h` und modulübergreifende Bündelung von Rust (`kr_rust/src/lib.rs`) und Go (`kr_go/main.go`).
- **Lückenloser Cross-File & Cross-Language FFI**: Funktionen rufen einander über Modul- und Sprachgrenzen ($C++ \leftrightarrow Rust \leftrightarrow Go$) direkt auf, ohne manuelle Header-Inkludierungen.
- **Integriertes vcpkg-Manifest**: Automatische Erzeugung und Verwaltung von `vcpkg.json` für Drittbibliotheken (`opencl`, `sqlite3`, `nlohmann-json`).
- **Physisch verifiziert**: Erfolgreich getestet auf Samsung Galaxy Tab S6 Lite (`SM-P615`), Realme GT 6T (`RMX3853`) und Windows 11 PC.

---

## Neu in Version 4.0.2: Universelle C/C++ & CMake Kompatibilität

Version 4.0.2 ist das universelle C/C++- und CMake-Sysroot-Kompatibilitäts-Release:
- **100 % Erfolgsquote an 18 realen C/C++ Repositories (54/54 Builds)**: Vollständige native Kompilierung aller getesteten Praxis-Repositories (`MDraft`, `Tatarus-6`, `mycelia_Craft`, `Algorithmic-Genesis`, `LyricsStudio`, etc.) für Android ARM64, Windows x64 und Linux x64 ohne Code-Anpassungen im Zielprojekt.
- **CMake & Sysroot Härtung**: Automatische Unterordner-Suche, Filterung restriktiver Compiler-/Linkerflags (`-Werror`, `/WX`), Bereitstellung vorkonfigurierter System-Header (`OpenCL`, `SQLite3`, `X11`) und Pfadaliasharmonisierung (`/data/data/` vs. `/data/user/0/`).
- **GCC-14 libstdc++ & MinGW Header-Patches**: Behebung rekursiver Typdefinitionen in `std::pair` unter Clang und Anhebung der Standard-Windows-APIs auf Win 10/11 (`0x0A00`) für moderne High-DPI-Funktionen.
- **Direkt-Build-Optimierung**: Unterstützung für getrennte C- und C++-Objekterzeugung, `-std=c++20` / `-std=gnu17` und automatische Shared-Library-Erzeugung.

---

## Neu in Version 4.0: KR Unified Language (`.kr`)

Mit Version 4.0.0 führt die IDE die **KR Unified Language (`.kr`)** ein. Entwickler können C++20, Rust und Go **ohne künstliche Sprachmarker** (`@cpp`, `@rust`, `@go`) in einer einzigen Datei frei kombinieren:

- **Deterministische Frontend-Pipeline**: Eigener `KrLexer`, `KrParser`, `KrSymbolTable` und `KrCodeGenerator`.
- **Automatische Sprachfusion**: Vollständige Auflösung von Kreuzaufrufen in allen 6 Richtungen ($C++ \leftrightarrow Rust \leftrightarrow Go$).
- **Automatische FFI-Brücke**: Generierung standardisierter C-ABIs (`kr_bridge.h`), Marshalling und Speichersicherheit.
- **Vier neue `.kr`-Projektvorlagen**:
  1. *KR Konsole* (Konsolenprogramm in `.kr`)
  2. *KR GUI mit SDL3* (Grafische Anwendung in `.kr`)
  3. *KR Sprachfusion Konsole* (C++20, Rust und Go interoperabel in einer Datei)
  4. *KR Sprachfusion GUI* (Dreifache Sprachfusion mit SDL3-Grafikoberfläche)

Zusätzlich werden reine **Rust-Projekte** (Cargo / `rustc`) und **Go-Projekte** (`go.mod` / `go build` / `cgo`) nativ unterstützt.

---

## Unterstützte Zielplattformen

| Projektkategorie | Android ARM64 | Windows x64 | Linux x64 |
|---|---|---|---|
| **KR Unified Language (`.kr`)** | APK | EXE | ELF |
| **KR Sprachfusion (C++ / Rust / Go)** | APK | EXE | ELF |
| **C und C++20 (CMake / Ninja)** | APK | EXE | ELF |
| **Rust (Cargo / rustc)** | APK | EXE | ELF |
| **Go (go build / CGO)** | APK | EXE | ELF |

| Rust Konsole auf Android | Go Konsole auf Android |
|---|---|
| ![Rust Konsole](docs/images/v4.0/rust-console.png) | ![Go Konsole](docs/images/v4.0/go-console.png) |

| Rust GUI auf Android | Go GUI auf Android |
|---|---|
| ![Rust GUI](docs/images/v4.0/rust-gui.png) | ![Go GUI](docs/images/v4.0/go-gui.png) |

---

## Schnellstart

1. **Neues Projekt anlegen**: Im Menü **Neues .kr-Projekt** oder **Neues Rust-/Go-Projekt** wählen.
2. **Vorlage bestimmen**: Z. B. *KR Sprachfusion (C++ / Rust / Go)* auswählen und Namen vergeben.
3. **Quelltext schreiben**: C++-, Rust- und Go-Funktionen nahtlos in der `.kr`-Datei definieren und aufrufen.
4. **Zielsystem wählen**: Android APK, Windows EXE oder Linux Programm einstellen.
5. **Kompilieren**: Mit einem Tippen auf **Kompilieren** den automatisierten Multi-Toolchain-Build starten.
6. **Ausführen & Teilen**: Unter **Artefakte** die Android-App direkt installieren/starten oder Desktop-Dateien exportieren.

---

## Vollständige Dokumentation im Repository

Alle Handbücher, Architekturberichte und Testprotokolle stehen im Ordner [`docs/`](docs/) bereit:

- **[KR Unified Language Entwickler-Leitfaden](docs/KR_UNIFIED_LANGUAGE_GUIDE.md)**: Vollständige Sprachfusionsregeln, Speicherverwaltung, Datentypen, FFI-Konventionen und Best Practices für `.kr`.
- **[Benutzerhandbuch v4.0.3 (Markdown)](docs/BENUTZERHANDBUCH_v4.0.3.md)**: Bebilderte Anleitung für die mobile IDE und Desktop Edition inkl. Autonomer KI-Agent, ReAct-Loop, LM Studio & Ollama.
- **[Benutzerhandbuch v4.0.3 (Word docx)](docs/KR_Compiler_Android_Benutzerhandbuch_v4.0.3_16-10-2026.docx)**: Vollständiges formatiertes Benutzerhandbuch.
- **[Technische Dokumentation v4.0.3 (Markdown)](docs/DOKUMENTATION_v4.0.3.md)**: Architektur, KrSourceMap-Rückführung, FFI-Pipeline, Windows Desktop Parität und ReAct-Loop-Spezifikation.
- **[Technische Dokumentation v4.0.3 (Word docx)](docs/KR_Compiler_Android_Dokumentation_v4.0.3_16-10-2026.docx)**: Vollständige technische Dokumentation als Word-Dokument.
- **[Testprotokoll v4.0.3](docs/TESTPROTOKOLL_v4.0.3.md)**: Verifikation von Multi-Language-Enterprise-Projekten (KR_NEXUS_ENTERPRISE, 5,25 MB) und E2E-Autonom-Reparatur mit LM Studio (gemma-4-e2b).
- **[Release Notes v4.0.3](docs/RELEASE_NOTES_v4.0.3.md)**: Offizielle Versionshinweise und Änderungsprotokoll zu v4.0.3.
- **[Projektgeschichte Kreta & KR IDE (Word docx)](docs/KR_Compiler_Android_Kreta_Geschichte_v4.0.3_16-10-2026.docx)**: Entstehungsgeschichte und Evolution der mobilen Entwicklungsumgebung.

---

## Repository forken und selbst bauen

Benötigt werden Windows x64, PowerShell 7+, JDK 17 sowie das Android SDK mit API 35, Build Tools und NDK `27.0.12077973`.

```powershell
git clone https://github.com/DEIN-NAME/KR-Compiler.git
cd KR-Compiler
powershell -ExecutionPolicy Bypass -File .\android\scripts\build-apk.ps1
```

Das Skript erstellt die signierte Release-APK `KR-Compiler-Android-IDE-v4.0.0.apk`.

---

## Lizenz & Dritte

Der Quellcode von KR Compiler Android IDE steht unter der **GPL-3.0**. Eingebettete Open-Source-Komponenten (LLVM/Clang, Rust, Go, SDL3, Dear ImGui, MinGW-w64) unterliegen ihren jeweiligen Lizenzen; Einzelheiten finden sich in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
