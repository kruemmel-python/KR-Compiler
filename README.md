# KR Compiler Android IDE

KR Compiler Android IDE ist eine mobile Entwicklungsumgebung für C, C++, Rust und Go auf ARM64-Android-Geräten. Projekte werden direkt auf dem Smartphone bearbeitet und für Android ARM64, Windows x64 oder Linux x64 gebaut. Konsolenprogramme und grafische SDL3-Anwendungen werden unterstützt.

## Version 4.0.0 herunterladen

Die installierbare APK, das bebilderte Benutzerhandbuch, die technische Dokumentation und ein Paket aller zwölf geprüften Beispiel-Artefakte befinden sich im [GitHub-Release v4.0.0](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.0.0).

Voraussetzungen:

- Android 8.0 oder neuer
- ARM64-Gerät (`arm64-v8a`)
- rund 3 GiB freier Speicher für APK, entpackte Werkzeugkette und Builds

## Neu in Version 4.0

Über das Projektmenü können vier neue Vorlagen angelegt werden:

- Rust Konsole
- Rust GUI mit SDL3
- Go Konsole
- Go GUI mit SDL3

Die IDE erkennt `Cargo.toml`, Rust-Quelldateien, `go.mod` und Go-Quelldateien automatisch. Rust wird mit `rustc` und Cargo gebaut, Go mit `go build`. Für grafische Programme verbindet die IDE die Sprachbibliothek mit dem vorhandenen SDL3-Runner.

| Sprache | Android ARM64 | Windows x64 | Linux x64 |
|---|---|---|---|
| C und C++ | APK | EXE | ELF |
| Rust Konsole | APK | EXE | ELF |
| Rust GUI | APK | EXE | ELF |
| Go Konsole | APK | EXE | ELF |
| Go GUI | APK | EXE | ELF |

| Rust Konsole auf Android | Go Konsole auf Android |
|---|---|
| ![Rust Konsole](docs/images/v4.0/rust-console.png) | ![Go Konsole](docs/images/v4.0/go-console.png) |

| Rust GUI auf Android | Go GUI auf Android |
|---|---|
| ![Rust GUI](docs/images/v4.0/rust-gui.png) | ![Go GUI](docs/images/v4.0/go-gui.png) |

## Bedienung

1. Im Menü **Neues Rust-/Go-Projekt** wählen.
2. Sprache und Projekttyp auswählen.
3. In der Build-Ansicht Android APK, Windows EXE oder Linux Programm einstellen.
4. **Kompilieren** antippen.
5. Das Ergebnis unter **Artefakte** installieren, teilen oder kopieren.

Die vollständige Schrittfolge steht im [bebilderten Benutzerhandbuch v4.0](docs/BENUTZERHANDBUCH_v4.0.md).

## Mobile Entwicklungsumgebung

Die IDE bietet einen Quelltexteditor, Dateireiter, Projektverwaltung, Suche und Ersetzen, GitHub-Import, Buildprotokolle und eine Artefaktansicht. Hoch- und Querformat passen sich automatisch an; im Querformat bleibt der Editor auch bei TV-Spiegelung nutzbar.

C++-GUI-Projekte verwenden weiterhin SDL3, Dear ImGui und die integrierte vcpkg-Verwaltung. Rust- und Go-GUI-Vorlagen verwenden SDL3 über eine kleine native Brücke, damit derselbe Projektquelltext für alle drei Ziele gebaut werden kann.

## Geprüfter Stand

Alle zwölf Kombinationen aus Rust oder Go, Konsole oder GUI und Android, Windows oder Linux wurden auf dem angeschlossenen RMX3853 erzeugt. Die Android-APKs wurden installiert und gestartet. Die Windows-Konsolenprogramme liefen nativ, die Linux-Konsolenprogramme unter Ubuntu. Beide Windows-GUI-Programme öffneten ein Fenster; die Linux-GUI-Dateien wurden als x86-64-ELF geprüft und gestartet.

Die Einzelgrößen und Prüfschritte stehen im [Testprotokoll v4.0](docs/TESTPROTOKOLL_v4.0.md).

## Dokumentation

- [Bebildertes Benutzerhandbuch v4.0](docs/BENUTZERHANDBUCH_v4.0.md)
- [Benutzerhandbuch v4.0 als Word-Datei](docs/KR_Compiler_Android_Benutzerhandbuch_v4.0_15-10-2026.docx)
- [Technische Dokumentation v4.0](docs/KR_Compiler_Android_Dokumentation_v4.0_15-10-2026.docx)
- [Testprotokoll v4.0](docs/TESTPROTOKOLL_v4.0.md)
- [Projektgeschichte Kreta und KR IDE v4.0](docs/KR_Compiler_Android_Kreta_Geschichte_v4.0_15-10-2026.docx)
- [Technische Hinweise zum Android-Projekt](android/README.md)

## Repository forken und selbst bauen

Benötigt werden Windows, PowerShell, JDK 17 sowie das Android SDK mit API 35, Build Tools und NDK `27.0.12077973`.

```powershell
git clone https://github.com/DEIN-NAME/KR-Compiler.git
cd KR-Compiler
powershell -ExecutionPolicy Bypass -File .\android\scripts\build-apk.ps1
```

Das Skript erstellt `KR-Compiler-Android-IDE-v4.0.0.apk`. Für eine eigene dauerhafte Release-Signatur können `KR_SIGNING_STORE`, `KR_SIGNING_STORE_PASSWORD`, `KR_SIGNING_KEY_ALIAS` und `KR_SIGNING_KEY_PASSWORD` gesetzt werden. Private Schlüssel werden nicht im Repository gespeichert.

## Projektstruktur

- `android/app`: Editor, Projektverwaltung, Compilersteuerung und APK-Paketierung
- `android/runner`: Laufzeit-App für erzeugte Android-Programme
- `android/scripts`: Werkzeugpaket und reproduzierbarer APK-Build
- `android/tests`: CMake-, Abhängigkeits- und Kernfunktionstests
- `docs`: Handbücher, Testprotokolle und Android-Screenshots

## Sicherheit und Lizenzen

Online-Abhängigkeiten und GitHub-Importe sind ausdrückliche Benutzeraktionen. Fremde Projektdateien und Buildskripte können Befehle ausführen und sollten nur aus vertrauenswürdigen Quellen geöffnet werden.

Der Quellcode steht unter GPL-3.0. Eingebettete Komponenten verwenden eigene Open-Source-Lizenzen; Einzelheiten stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
