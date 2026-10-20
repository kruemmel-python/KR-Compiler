# MDraft KR Cross Platform

Dieses Beispiel ist die modulare KR-Portierung des bereitgestellten
MDraft-Codedumps. Es ist eine echte SDL3-GUI-Anwendung und kein
Konsolenprogramm oder Web-Wrapper.

## Plattformen

- Android ARM64 als startbare SDL3-Runner-APK
- Windows x64 als native Anwendung
- Linux x64 als native Anwendung

Das ist **ein einziges Projekt** für alle drei Ziele. Es gibt keine getrennten
Windows-, Android- oder Linux-Quellbäume. Das Ziel wird beim Bauen in der KR
Compiler IDE gewählt. Das Projekt selbst lädt keine SDKs oder Bibliotheken
herunter.

## Module

```text
main.kr                         Programmeinstieg / SDL_main
include/mdraft/mdraft.hpp       gemeinsame Modul-API
src/app/application.kr          SDL3-Fenster, Eingabe und Ereignisschleife
src/core/document.kr            UTF-8-fähiger GapBuffer und Cursorbewegung
src/core/markdown.kr            Markdown-Blockanalyse und Vorschautext
src/io/file_io.kr               Laden und atomisches Speichern
src/ui/bitmap_font.kr           eingebetteter 5x7-Bitmap-Font
src/ui/editor_view.kr           Editor- und Preview-Darstellung
src/ui/theme_compute.kr         automatisch erkannte FP32-Theme-Berechnung
```

Der GapBuffer und die Markdown-Blockanalyse wurden aus den entsprechenden
`legacy/core`-Komponenten des gelieferten ZIPs portiert. Die früher getrennten
Win32- und X11-Oberflächen wurden durch eine gemeinsame SDL3-Projektion
ersetzt. `SDL3_ttf` wurde bewusst entfernt, weil es nicht Bestandteil aller
drei portablen Toolchains ist; der integrierte Bitmap-Font benötigt keine
Systemschrift und funktioniert auch im Android-Runner.

## Bedienung

- Text direkt eingeben; unter Android wird die SDL-Texteingabe verwendet.
- `Tab` oder die Schaltfläche **VORSCHAU** wechselt zwischen Quelltext und
  Markdown-Vorschau.
- `Ctrl+S` oder **SPEICHERN** schreibt die Datei atomisch.
- `Ctrl+N` oder **NEU** setzt das Beispieldokument zurück.
- Pfeiltasten, Pos1, Ende, Bild auf/ab und Mausrad bewegen Cursor und Ansicht.
- Ein beim Programmstart übergebener Dateipfad wird geladen.

Auf Android speichern **SPEICHERN** und das Standarddokument in den privaten,
beschreibbaren Projektordner des KR-Runners.

## Bauen

Den Ordner `examples/MDraft_KR_CrossPlatform` in KR Compiler IDE öffnen und
als Ziel Android ARM64, Windows x64 oder Linux x64 wählen. Die Datei
`MDraft_KR_CrossPlatform.krproject` markiert das Projekt ausdrücklich als
`target_type = "gui"` und lässt alle `.kr`-Module unter `src/` automatisch
einsammeln.

Die tatsächlich durchgeführten Builds stehen in `TEST_RESULTS.md`.
Eine vollständige Anleitung für eigene modulare Projekte enthält
`DEVELOPER_GUIDE.md`.
