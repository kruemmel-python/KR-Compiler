# KR Compiler Android IDE

KR Compiler Android IDE ist eine autarke, native mobile Entwicklungsumgebung für **KR Unified Language (`.kr`)**, **C++20**, **Rust** und **Go** auf ARM64-Android-Geräten. Projekte werden direkt auf dem Smartphone erstellt, analysiert und für **Android ARM64**, **Windows x64** oder **Linux x64** gebaut. Sowohl reine Konsolenprogramme als auch hardwarebeschleunigte grafische **SDL3-Anwendungen** werden nativ unterstützt.

---

## Version 4.0.2 herunterladen

Die installierbare APK, die bebilderten Benutzerhandbücher, die technische Dokumentation und die geprüften Beispiel-Artefakte befinden sich im [GitHub-Release v4.0.2](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.0.2).

- **Release-APK**: `KR-Compiler-Android-IDE-v4.0.2.apk`
- **SHA-256**: `B22B6035425CCA7F1539B507E99F66673E2E0B9610E90371256B33D28A25DE51`

### Systemvoraussetzungen
- Android 8.0 oder neuer (API 26+)
- 64-Bit ARM-Gerät (`arm64-v8a`)
- Ca. 3 GiB freier interner Speicher für Toolchains, SDKs und Build-Cache

---

## Neu in Version 4.0.2

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
- **[Benutzerhandbuch v4.0 (Markdown)](docs/BENUTZERHANDBUCH_v4.0.md)**: Bebilderte Schritt-für-Schritt-Anleitung für die mobile IDE.
- **[Benutzerhandbuch v4.0 (Word docx)](docs/KR_Compiler_Android_Benutzerhandbuch_v4.0_15-10-2026.docx)**: Vollständiges formatiertes Benutzerhandbuch.
- **[Technische Dokumentation v4.0 (Markdown)](docs/DOKUMENTATION_v4.0.md)**: Architektur, Pipeline-Stufen, ABI-Design, Compiler-Orchestrierung und Plattform-Profile.
- **[Technische Dokumentation v4.0 (Word docx)](docs/KR_Compiler_Android_Dokumentation_v4.0_15-10-2026.docx)**: Vollständige technische Dokumentation als Word-Dokument.
- **[Testprotokoll v4.0](docs/TESTPROTOKOLL_v4.0.md)**: Verifikation aller 18 Zielkombinationen sowie des rechenintensiven DSP-Algorithmus (RoPE Attention) auf dem realen Smartphone RMX3853.
- **[Release Notes v4.0.0](docs/RELEASE_NOTES_v4.0.0.md)**: Offizielle Versionshinweise und Änderungsprotokoll.
- **[Projektgeschichte Kreta & KR IDE (Word docx)](docs/KR_Compiler_Android_Kreta_Geschichte_v4.0_15-10-2026.docx)**: Entstehungsgeschichte und Evolution der mobilen Entwicklungsumgebung.

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
