# KR Compiler Android IDE v3.0.0

Version 3.0 macht aus dem mobilen Mehrziel-Compiler eine integrierte C++-Entwicklungsumgebung für Android-Smartphones und -Tablets.

## Neu in Version 3.0

- integrierter Quelltexteditor mit Dateireitern und C/C++-Syntaxdarstellung
- mobile Symbolleiste, automatische Klammern und automatische Einrückung
- Suche und Ersetzen sowie direkte Navigation zu einer Zeilennummer
- Projekt- und Dateiansicht mit neuen Dateien und Unterordnern
- Import öffentlicher GitHub-Repositories
- getrennte Arbeitsbereiche für Code, Dateien, Build und Artefakte
- fortlaufende Compiler- und Linkerausgabe mit Kopierfunktion
- Artefaktansicht für Programme, `build.log` und `build-result.json`
- dunkles Design, optionale Zeilenumbrüche und anpassbare Schriftgröße
- adaptive Hoch- und Querformatdarstellung

## Weiterhin enthalten

- Clang/LLVM 21.1.8 mit C++20-Unterstützung
- CMake 4.4.4, Ninja 1.13.2, Git und pkg-config
- Zielumgebungen für Android ARM64, Windows x64 und Linux x64
- CMake-Auswertung einschließlich `FetchContent`
- signierte Android-Runner-APKs mit eingebetteten Projektressourcen
- optionaler, geprüfter Download bekannter CMake-Abhängigkeiten

## Release-Dateien

- `KR-Compiler-Android-IDE-v3.0.apk`: installierbare Android-App
- `KR-Compiler-Android-IDE-v3.0.apk.sha256`: SHA-256-Prüfsumme der APK
- `KR_Compiler_Android_Dokumentation_v3.0_14-10-2026.docx`: technische Dokumentation mit allen aktuellen Android-Screenshots
- `KR_Compiler_Android_Kreta_Geschichte_v3.0_14-10-2026.docx`: aktualisierte Projektgeschichte mit v3.0-Bildstrecke

## SHA-256 der APK

`acb77416b60cefc31be31b0b2652bd7a3cb2ca114e51464f8cef8b7d567d7ff1`

Die APK ist mit dem KR-Compiler-Entwicklungsschlüssel signiert und wurde vor der Veröffentlichung mit `apksigner` geprüft. Forks erzeugen beim ersten lokalen Build automatisch einen eigenen Entwicklungsschlüssel. Die Compilerlaufzeit wird weiterhin geprüft aus Release v2.6.5 geladen.
