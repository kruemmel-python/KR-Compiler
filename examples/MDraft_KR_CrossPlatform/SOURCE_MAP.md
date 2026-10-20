# Zuordnung zum gelieferten MDraft-Codedump

| Ursprünglicher Bereich | Portiertes KR-Modul |
|---|---|
| `legacy/core/gap_buffer` | `src/core/document.kr` |
| `legacy/core/markdown` | `src/core/markdown.kr` |
| `legacy/io/file_io` | `src/io/file_io.kr` |
| `legacy/runtime/editor_runtime` | `src/app/application.kr` |
| `legacy/ui_model/editor_state` | gemeinsame Typen in `include/mdraft/mdraft.hpp` |
| `legacy/gui_win32`, `legacy/gui_x11` | gemeinsame SDL3-Projektion in `src/ui` |

Die Plattform-GUIs wurden nicht als parallele Win32-/X11-Codepfade kopiert,
sondern auf eine portable SDL3-Oberfläche zusammengeführt. Dadurch verwendet
dasselbe modulare `.kr`-Projekt auf Android, Windows und Linux identischen
Anwendungscode.
