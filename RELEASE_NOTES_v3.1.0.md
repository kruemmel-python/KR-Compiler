# KR Compiler Android IDE v3.1.0

Version 3.1 erweitert die mobile C/C++-IDE um einen geführten Arbeitsablauf für grafische Desktop-Anwendungen und eine integrierte vcpkg-Manifestverwaltung.

## Neu in Version 3.1

- „Neues GUI-Projekt“ erzeugt eine startfähige SDL3-/Dear-ImGui-Vorlage
- „vcpkg-Bibliotheken“ verwaltet Abhängigkeiten direkt in der Android-Oberfläche
- automatische Einbindung von `vcpkg.json` und der vcpkg-CMake-Toolchain
- Windows-x64-GUI-Profil mit MinGW
- Linux-x64-GUI-Profil mit X11/Xext
- reproduzierbares GUI-Testprojekt unter `android/tests/gui-vcpkg`
- neues bebildertes Benutzerhandbuch mit allen 13 Bedien- und Testschritten
- aktualisierte technische Dokumentation und Projektgeschichte

## Durchgeführte Tests

- Release-APK erfolgreich gebaut und signiert
- App auf einem physischen Android-Gerät RMX3853 installiert und bedient
- identisches SDL3-/Dear-ImGui-Testprojekt für Linux x64 gebaut
- Linux-Ergebnis: `programm`, ELF x86-64, etwa 4,1 MB
- identisches Testprojekt für Windows x64 gebaut
- Windows-Ergebnis: `programm.exe`, PE32+ x86-64, etwa 5,2 MB

## Release-Dateien

- `KR-Compiler-Android-IDE-v3.1.apk`
- `KR-Compiler-Android-IDE-v3.1.apk.sha256`
- `KR-Compiler-Android-toolchain-v3.1.0.zip`
- `KR-Compiler-Android-toolchain-v3.1.0.zip.sha256`
- `KR_Compiler_Android_Benutzerhandbuch_v3.1_15-10-2026.docx`
- `KR_Compiler_Android_Dokumentation_v3.1_15-10-2026.docx`
- `KR_Compiler_Android_Kreta_Geschichte_v3.1_15-10-2026.docx`

## SHA-256

- APK: `592b3a21e9f1d97acfd7c9ab848d74584a8e873a938190763b33628c1ae4ee0c`
- Werkzeugpaket: `bb1b24d80d7ffbbcbe24e96ada527370f97bd9d169a97ffcd9d78b9670810b08`

Die APK ist mit dem KR-Compiler-Entwicklungsschlüssel signiert. Forks erzeugen beim ersten lokalen Build automatisch einen eigenen Entwicklungsschlüssel.
