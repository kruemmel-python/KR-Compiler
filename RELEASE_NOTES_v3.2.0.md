# KR Compiler Android IDE v3.2.0

Version 3.2 erweitert den bisherigen Windows-/Linux-GUI-Pfad um echte native Android-GUI-Anwendungen. Ein gemeinsames CMake-Projekt mit SDL3 und Dear ImGui kann nun direkt auf dem Smartphone für Android ARM64, Windows x64 und Linux x64 gebaut werden.

## Neu

- Android-GUI-Ziel für SDL3 und Dear ImGui
- offizieller SDLActivity-Startpfad mit Touch-, Eingabe-, Audio- und Lebenszyklusanbindung
- direkt installierbare, signierte Android-ARM64-Runner-APK
- automatische EGL-, GLES-, GLES2-, GLES3- und KHR-Header aus dem offiziellen Android-NDK
- gemeinsame GUI-Vorlage für Android, Windows und Linux
- neue Android-Diagnosen für X11-/Wayland-, Linker-, GLES- und Signaturprobleme
- elf neue Gerätescreenshots und ein vollständig aktualisiertes Benutzerhandbuch

## Geprüft

- Android-GUI-Projekt auf RMX3853 vollständig in der App kompiliert: 260 native Buildschritte
- `programm-android-arm64.apk` erzeugt, installiert und erfolgreich gestartet
- SDL3 und Dear ImGui öffnen ein natives Android-Fenster
- vorhandene Windows-x64- und Linux-x64-GUI-Pfade bleiben erhalten
- Gradle-Tests und Release-Build erfolgreich

## Dokumentation

- bebildertes Online-[Benutzerhandbuch v3.2](docs/BENUTZERHANDBUCH_v3.2.md)
- Benutzerhandbuch v3.2 als Word-Datei
- technische Dokumentation v3.2 als Word-Datei
- aktualisierte Projektgeschichte mit dem privaten Entwicklungsalltag nach der regulären Arbeit bei der E-Post

## Hinweis zur Laufzeit

Das große, geprüfte Compiler-Werkzeugpaket aus Release v3.1.0 bleibt unverändert. Die für Android-GUI benötigten Grafik-Header und der neue Runner sind in der v3.2-App enthalten.
