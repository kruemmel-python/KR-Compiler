# Benutzerhandbuch

Dieses Handbuch beschreibt den aktuellen Stand der KR Compiler IDE v4.3.0 für
Android, Windows und Linux.

## Installation

- **Android:** `KR-Compiler-Android-IDE-v4.3.0.apk` installieren. Mindestens
  Android 8.0/API 26 und ARM64 werden vorausgesetzt.
- **Windows:** ZIP entpacken und `Start-KR-IDE.bat` starten.
- **Linux:** `tar.xz` entpacken, `Start-KR-IDE.sh` ausführbar machen und starten.

Die vollständigen Distributionen bringen Java, Compiler und Zielsysteme mit.
Ein System-JDK oder ein nachträglicher SDK-Download ist nicht erforderlich.

## Projekt öffnen

Mit **Ordner öffnen** wird der Stammordner des Projekts ausgewählt. Auf Android
erfolgt der Zugriff über die System-Ordnerauswahl. Die IDE speichert das
Zugriffsrecht und spiegelt den Inhalt für Analyse und Build in einen internen
Arbeitsordner. Das Build-Ergebnis wird als `KR_BUILD` zurückgeschrieben.

## `.krproject`

Eine minimale Projektdatei sieht so aus:

```ini
[project]
name = "MeinProjekt"
version = "1.0.0"
entry = "main.kr"
target_type = "console"

[build]
cpp_standard = "c++20"

[compute]
mode = "AUTO"
```

Unter `[modules]` können weitere Quellverzeichnisse und unter
`[dependencies]` vcpkg-Pakete eingetragen werden.

## Ein SDL3-GUI-Projekt für drei Ziele

Ein GUI-Projekt bleibt ein gemeinsamer Projektordner. Es braucht weder drei
`.krproject`-Dateien noch getrennte Android-, Windows- und Linux-Quellbäume:

```ini
[project]
name = "MeineGui"
version = "1.0.0"
entry = "main.kr"
target_type = "gui"

[modules]
source_dirs = ["src"]

[dependencies]
packages = ["sdl3"]
```

Quellmodule binden SDL mit `#include <SDL3/SDL.h>` ein. Für einen portablen
Programmeinstieg wird zusätzlich `#include <SDL3/SDL_main.h>` empfohlen. Die
Zielplattform wird ausschließlich in der IDE ausgewählt. Version 4.3.0 enthält
SDL 3.4.18 samt benötigten Headern, Bibliotheken und Runnern; ein separater
SDL-, Android- oder Linux-Entwicklungsdownload ist nicht nötig.

Nach dem Build liegen die plattformspezifischen Ergebnisse gemeinsam unter
`KR_BUILD`: Windows erhält `programm.exe` und `SDL3.dll`, Linux `programm` und
`libSDL3.so.0`, Android eine installierbare Runner-APK. Das vollständige
Referenzprojekt steht unter
[`examples/MDraft_KR_CrossPlatform`](../examples/MDraft_KR_CrossPlatform/).

## Kompilieren

1. Projektordner öffnen.
2. Android APK, Windows x64 oder Linux x64 als Ziel wählen.
3. **Kompilieren** starten.
4. Meldungen im Build-Bereich prüfen.
5. Das Ergebnis unter **Artefakte** öffnen, installieren oder exportieren.

Die IDE verarbeitet `.kr`, C/C++, CMake, Rust/Cargo und Go. Bei `.kr`-Projekten
werden Sprachbereiche analysiert, Symbole aufgelöst, ABI-Brücken erzeugt und
die nativen Module anschließend verbunden.

## Adaptive Compute

**Adaptive Compute analysieren** zeigt erkannte Schleifen und den Grund für
GPU-Freigabe oder CPU-Pflicht. Die Modi bedeuten:

- `AUTO`: GPU ab ausreichender Arbeitsgröße; sicherer CPU-Fallback erlaubt.
- `CPU_ONLY`: ausschließlich CPU-Referenzpfad.
- `GPU_ONLY`: GPU ist zwingend; Fehler statt stillem CPU-Fallback.
- `VERIFY`: GPU-Ergebnis wird mit der CPU-Referenz verglichen.

Das Beispiel [`KR_AUTO_COMPUTE_TEST`](../KR_AUTO_COMPUTE_TEST/) demonstriert
automatische FP32-Addition und -Multiplikation ohne manuelle Runtime-Aufrufe.

## Agent

Der integrierte Agent kann Projektdateien lesen, Änderungen vorschlagen und
Builds anstoßen. Schreibende Aktionen benötigen eine Freigabe. API-Schlüssel
werden nicht in Projektdokumente geschrieben. Vor dem Übernehmen größerer
Änderungen sollte das Projekt versioniert oder gesichert sein.

## Fehlerdiagnose

- **Kein Projektordner:** den tatsächlichen Projektstamm erneut öffnen.
- **Build-Artefakt fehlt:** erste Fehlermeldung im Build-Log beheben.
- **GPU nicht verfügbar:** `AUTO` nutzt CPU; `GPU_ONLY` meldet einen Fehler.
- **Linux startet nicht:** Ausführungsrecht für `Start-KR-IDE.sh` setzen.
- **Android hat zu wenig Platz:** mehrere GiB internen Speicher freihalten.
- **SDL3-Header fehlt:** prüfen, ob wirklich das vollständige v4.3.0-Paket
  verwendet und `packages = ["sdl3"]` eingetragen wurde.
