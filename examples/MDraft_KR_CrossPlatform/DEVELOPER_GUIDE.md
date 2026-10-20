# Modulare KR-GUI-Projekte mit der aktuellen IDE

Diese Anleitung beschreibt den Stand der KR Compiler IDE v4.2.2 anhand des
laufenden MDraft-Beispiels. Das wichtigste Architekturprinzip lautet:

> Ein Projekt, eine `.krproject`, ein gemeinsamer Quellbaum – die IDE erzeugt
> daraus wahlweise Windows x64, Android ARM64 oder Linux x64.

## 1. Projektstruktur

```text
MeinProjekt/
├── MeinProjekt.krproject
├── main.kr
├── include/meinprojekt/api.hpp
└── src/
    ├── app/application.kr
    ├── core/model.kr
    ├── io/file_io.kr
    └── ui/view.kr
```

`main.kr` bleibt klein. Fachlogik, Ein-/Ausgabe und GUI werden in getrennte
Module gelegt. Gemeinsam benutzte Deklarationen stehen in einem Header unter
`include/`. Dateien dürfen weitere Unterordner bilden; der Modulscan arbeitet
rekursiv.

## 2. Die `.krproject`-Datei

```ini
[project]
name = "MeinProjekt"
version = "1.0.0"
entry = "main.kr"
target_type = "gui"

[modules]
scan_directories = ["src"]

[dependencies]
packages = ["sdl3"]

[build]
cpp_standard = "c++20"
optimization = "O2"

[compute]
mode = "AUTO"
```

- `entry` benennt genau einen Programmeinstieg.
- `target_type = "gui"` aktiviert die GUI-Ausgabe und auf Android den
  SDL3-GUI-Runner.
- `scan_directories` sammelt alle `.kr`-Module unter den angegebenen Ordnern.
- `packages = ["sdl3"]` aktiviert Header, Linker und Laufzeitkopie für SDL3.
- `compute.mode` kann `AUTO`, `CPU_ONLY`, `GPU_ONLY` oder `VERIFY` sein.

Keine Betriebssystempfade in die Projektdatei schreiben. Insbesondere gehören
Android-SDK-, NDK-, Windows- oder Ubuntu-Pfade nicht in ein portables Projekt.

## 3. Gemeinsamer Einstiegspunkt

```cpp
#include "include/meinprojekt/api.hpp"
#include <SDL3/SDL_main.h>

int main(int argc, char** argv) {
    return meinprojekt::run_application(argc, argv);
}
```

`SDL_main.h` ist für den Android-GUI-Runner wichtig. Trotzdem bleibt die
normale `main`-Funktion der einzige Einstiegspunkt für alle Ziele. Es werden
keine `main_windows.kr`, `main_android.kr` oder `main_linux.kr` angelegt.

## 4. Öffentliche Modul-API

```cpp
#pragma once
#include <SDL3/SDL.h>
#include <string>

namespace meinprojekt {
struct AppState {
    bool running = true;
    std::string status = "Bereit";
};

int run_application(int argc, char** argv);
void render(SDL_Renderer* renderer, const AppState& state);
}
```

Jedes `.kr`-Modul bindet denselben Header ein und implementiert nur seinen
Teil. Namensräume verhindern Kollisionen mit anderen Modulen und Bibliotheken.

## 5. SDL3-GUI statt drei nativer Oberflächen

```cpp
int meinprojekt::run_application(int argc, char** argv) {
    if (!SDL_Init(SDL_INIT_VIDEO | SDL_INIT_EVENTS)) return 1;

    SDL_Window* window = SDL_CreateWindow(
        "Meine KR GUI", 960, 640,
        SDL_WINDOW_RESIZABLE | SDL_WINDOW_HIGH_PIXEL_DENSITY);
    if (!window) { SDL_Quit(); return 2; }

    SDL_Renderer* renderer = SDL_CreateRenderer(window, nullptr);
    if (!renderer) {
        SDL_DestroyWindow(window);
        SDL_Quit();
        return 3;
    }

    AppState state;
    SDL_StartTextInput(window);
    while (state.running) {
        SDL_Event event;
        while (SDL_PollEvent(&event)) {
            if (event.type == SDL_EVENT_QUIT) state.running = false;
            if (event.type == SDL_EVENT_TEXT_INPUT) {
                // event.text.text verarbeiten
            }
            if (event.type == SDL_EVENT_MOUSE_BUTTON_DOWN) {
                // event.button.x / event.button.y verarbeiten
            }
            if (event.type == SDL_EVENT_FINGER_DOWN) {
                // normierte Touch-Koordinaten auf Fenstergröße abbilden
            }
        }
        render(renderer, state);
        SDL_RenderPresent(renderer);
        SDL_Delay(16);
    }

    SDL_StopTextInput(window);
    SDL_DestroyRenderer(renderer);
    SDL_DestroyWindow(window);
    SDL_Quit();
    return 0;
}
```

Maus und Touch sollten beide behandelt werden. Für Android muss die
Texteingabe mit `SDL_StartTextInput(window)` aktiviert werden. Dateipfade
dürfen nicht auf Laufwerksbuchstaben oder ein festes Home-Verzeichnis
festgelegt werden.

## 6. Schrift ohne zusätzliche Installation

`SDL3_ttf` gehört derzeit nicht zu allen drei portablen Toolchains. Dieses
Beispiel verwendet deshalb einen eingebetteten 5x7-Bitmap-Font. So benötigt
die Anwendung weder eine Systemschrift noch eine zusätzliche Bibliothek. Wer
`SDL3_ttf` einsetzen will, muss es zuerst als Bestandteil aller drei
IDE-Toolchains ausliefern; ein Download während des Projekt-Builds wäre nicht
portabel.

## 7. Automatische Adaptive-Compute-Schleifen

Der Anwendungscode ruft keine `kr_compute_*`-Funktion auf. Eine geeignete
FP32-Schleife sieht normal aus:

```cpp
void add_values(float* output, const float* left,
                const float* right, int count) {
    for (int index = 0; index < count; index++) {
        output[index] = left[index] + right[index];
    }
}
```

Die KR-Pipeline analysiert Parser- und Symbolinformationen und ersetzt sichere
Schleifen durch die vorhandene Runtime. `AUTO` wählt CPU oder GPU,
`CPU_ONLY` erzwingt CPU, `GPU_ONLY` verbietet stillen CPU-Fallback und
`VERIFY` vergleicht Ergebnisse. Zeigerbereiche dürfen sich nicht unerwartet
überlappen; Schleifengrenzen und Indizes müssen eindeutig sein. Unsichere
Schleifen bleiben unverändert auf dem CPU-Pfad.

Im MDraft-Beispiel befindet sich eine solche normale Schleife in
`src/ui/theme_compute.kr`. Sie berechnet Farbkanäle, ohne die Runtime-API
direkt zu kennen.

## 8. Bauen in der IDE

1. In der IDE **Ordner öffnen** wählen.
2. Den Ordner auswählen, der die `.krproject` enthält – nicht nur `src` und
   nicht die einzelne `main.kr`.
3. Im Zielmenü Windows x64, Android ARM64 oder Linux x64 wählen.
4. **Kompilieren** starten.
5. Die Ausgabe liegt im erzeugten `KR_BUILD`-Ordner.

Typische Artefakte sind `programm.exe` plus `SDL3.dll` unter Windows,
`programm-android-arm64.apk` unter Android und `programm` plus die benötigte
SDL3-Laufzeit unter Linux. `KR_BUILD` ist erzeugte Ausgabe und gehört nicht in
Git.

## 9. Aktueller Linux-SDL3-Stand

Beim Test mit dem portablen Linux-x64-Paket v4.2.2 fehlten
`toolchain/include/SDL3/SDL.h` und eine Linux-x64-`libSDL3`. Das ist eine
Lücke der Toolchain-Auslieferung, nicht des modularen Projektmodells. Ein
Entwickler soll deshalb **keinen Linux-Quellbaum und kein zweites Projekt**
anlegen. Die IDE-Auslieferung muss SDL3 an denselben logischen Stellen
mitbringen wie die Windows- und Android-Auslieferung. Danach wird dieselbe
Projektdatei unverändert verwendet.

## 10. Portabilitätsregeln

- Nur relative Projektpfade verwenden.
- Keine SDKs oder Pakete während des Builds herunterladen.
- Nur Abhängigkeiten deklarieren, die die portable IDE für jedes Ziel enthält.
- Gemeinsame APIs in Headern, Implementierungen in kleinen `.kr`-Modulen.
- Betriebssystemunterschiede über die mitgelieferte Laufzeitschicht kapseln.
- GUI-Ereignisse für Maus, Tastatur und Touch vorsehen.
- Schreibbare Laufzeitpfade verwenden; Installationsordner nicht als
  beschreibbar voraussetzen.
- Erzeugte Dateien (`KR_BUILD`) nicht als Quellcode einchecken.

Das vorliegende MDraft-Projekt ist das vollständige Referenzbeispiel für diese
Struktur.
