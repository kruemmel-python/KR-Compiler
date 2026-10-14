# KR Compiler für Android

KR Compiler ist eine vollständige Android-App, die C- und C++-Projekte direkt auf einem
ARM64-Android-Gerät baut. Ein Projekt wird über die Android-Dateiauswahl geöffnet; die Ergebnisse
landen im Projektordner unter `KR_BUILD`.

## Fertige App herunterladen

Die installierbare APK, die deutschsprachige Dokumentation und ein gebautes Beispiel befinden
sich im [GitHub-Release v2.6.5](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v2.6.5).

Voraussetzungen für die App:

- Android 8.0 oder neuer
- ARM64-Gerät (`arm64-v8a`)
- ungefähr 1 GB freier Speicher für Installation, entpackte Compilerlaufzeit und Builds

## Was die App kann

- `CMakeLists.txt` mit dem enthaltenen CMake und Ninja auswerten
- C/C++-Projekte ohne CMake als Einzelprogramm bauen
- Android-ARM64-Runner-APKs erzeugen und signieren
- Windows-x64-Programme (`programm.exe`) cross-kompilieren
- Linux-x64-Programme (`programm`) cross-kompilieren
- CMake `FetchContent` und Git-Zugriffe optional erlauben
- bekannte CMake-Pakete wie `fmt`, `nlohmann_json`, `spdlog`, `Catch2` und `Eigen3`
  kontrolliert herunterladen und im privaten App-Speicher zwischenspeichern
- Projektressourcen relativ zur ausführbaren Datei in eine Android-Runner-APK übernehmen

Ein CMake-Projekt muss für Android ein ausführbares Ziel mit `add_executable` bereitstellen.
Bei mehreren ausführbaren Zielen kann der gewünschte Name in `kr-android-target.txt` stehen.
Plattformgebundener Code und nicht vorhandene proprietäre SDKs können nicht automatisch portiert
werden.

## Repository forken und selbst bauen

Benötigt werden Windows, PowerShell, JDK 17 sowie das Android SDK mit API 35, Build Tools und
NDK `27.0.12077973`. Danach:

```powershell
git clone https://github.com/DEIN-NAME/KR-Compiler.git
cd KR-Compiler
powershell -ExecutionPolicy Bypass -File .\android\scripts\build-apk.ps1
```

Beim ersten Build lädt das Skript die etwa 369 MiB große, geprüfte Compilerlaufzeit aus dem
Release. Anschließend erzeugt es ausschließlich lokal einen Entwicklungsschlüssel. Weder der
Werkzeugkasten noch private Schlüssel werden im Git-Repository gespeichert. Die fertige APK
liegt unter `android/app/build/outputs/apk/release/app-release.apk` und zusätzlich als
`KR-Compiler-Android-v2.6.5.apk` im Repository-Arbeitsordner.

Für eine eigene dauerhafte Release-Signatur können diese Umgebungsvariablen gesetzt werden:

- `KR_SIGNING_STORE`
- `KR_SIGNING_STORE_PASSWORD`
- `KR_SIGNING_KEY_ALIAS`
- `KR_SIGNING_KEY_PASSWORD`

Die GitHub-Action unter `.github/workflows/android-build.yml` führt denselben Build bei jedem
Push und Pull Request aus. Ein Fork kann sie ohne zusätzliche Geheimnisse starten; das Ergebnis
wird als Workflow-Artefakt bereitgestellt.

## Projektstruktur

- `android/app`: Oberfläche, Projektimport, Compilersteuerung und APK-Paketierung
- `android/runner`: kleine Laufzeit-App für erzeugte Android-Programme
- `android/scripts`: Download, lokale Signierung und reproduzierbarer APK-Build
- `android/tests`: kleine CMake- und Abhängigkeitstests

Technische Einzelheiten stehen in [android/README.md](android/README.md). Die ausführliche
Benutzerdokumentation ist Teil des Releases.

## Sicherheit und Lizenzen

Online-Abhängigkeiten sind standardmäßig eine ausdrückliche Benutzerentscheidung. Fremde
Projektdateien und CMake-Skripte sind ausführbarer Buildinhalt und sollten nur aus
vertrauenswürdigen Quellen geöffnet werden.

Der Quellcode dieses Repositorys steht unter GPL-3.0. Die eingebettete Laufzeit enthält
Komponenten unter eigenen Open-Source-Lizenzen; siehe [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
und die Lizenzdateien innerhalb der jeweiligen Release-Komponenten.
