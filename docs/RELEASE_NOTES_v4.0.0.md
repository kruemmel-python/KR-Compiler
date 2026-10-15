# KR Compiler Android IDE v4.0.0 – Release Notes

Version 4.0.0 führt die bahnbrechende **KR Unified Language (`.kr`)** ein sowie vollständige Projektunterstützung für C++20, Rust und Go für Android ARM64, Windows x64 und Linux x64.

---

## Höhepunkte: KR Unified Language (.kr)

- **Freie Sprachfusion**: Entwickler schreiben C++20, Rust und Go nahtlos innerhalb einer einzigen `.kr`-Quelldatei, ohne künstliche Sprachdeklarationen wie `@cpp`, `@rust` oder `@go`.
- **Deterministische Frontend-Pipeline**:
  - `KrLexer`: Zuverlässige Unterscheidung von Tokens, Raw-Strings (C++, Rust `r#""#`, Go backticks), Kommentaren und Präprozessor.
  - `KrParser`: Strukturelle Erkennung von C++-, Rust- und Go-Deklarationen, Top-Level-Blöcken sowie vollständige Auflösung komplexer Funktionssignaturen mit Standardargumenten.
  - `KrSymbolTable`: Sprachübergreifende Symbolauflösung, Typverifikation und Erkennung von Kreuzaufrufen in allen 6 Richtungen (C++ ↔ Rust, C++ ↔ Go, Rust ↔ Go).
  - `KrCodeGenerator`: Automatische Generierung einer C-kompatiblen ABI, typ-sicherer Wrapper und Module für alle beteiligten Original-Compiler.
- **Vier integrierte `.kr`-Projektvorlagen**:
  1. `KR Konsole`: Reine oder kombinierte Konsolenanwendung
  2. `KR GUI mit SDL3`: Portables grafisches SDL3-Fenster
  3. `KR Sprachfusion (C++ / Rust / Go)`: Echte dreifache Sprachinteroperabilität in Konsole
  4. `KR Sprachfusion GUI`: Dreifache Sprachinteroperabilität mit SDL3-Grafikoberfläche
- **Reine Rust- und Go-Projekte**:
  - Rust Konsole & Rust GUI (Cargo / rustc)
  - Go Konsole & Go GUI (go build, cgo)
- **Multi-Plattform-Kompilierung**:
  - **Android ARM64**: Direkt auf dem Gerät als APK gebaut, signiert und ausführbar (mittels Runner und nativer SDLActivity).
  - **Windows x64**: Als native `.exe` gelinkt mit MinGW-x64 und Windows-Systembibliotheken.
  - **Linux x64**: Als portable ELF-Binärdatei mit glibc, pthreads und X11/Wayland-Unterstützung.

---

## Vollständige End-to-End-Verifikation

Auf einem physischen Android-Smartphone (RMX3853) wurden alle **18 Kombinationen** (6 Projekttypen × 3 Zielplattformen) sowie komplexe rechenintensive Algorithmen (z. B. **RoPE + Online-Softmax Attention** mit C++-Tensorkern, Rust-RoPE-Frequenzberechnung und Go-Fehlerabnahme) erfolgreich kompiliert, installiert und ausgeführt:
- **Android APKs**: Installiert und ausgeführt via `kr.compile.runner`. Korrekte Bildschirmausgaben und SDL3-Oberflächen verifiziert.
- **Windows EXEs**: Ausgeführt und Ausgaben verifiziert:
  - C++ berechnet Ergebnis (54).
  - Rust verifiziert Ergebnis und benachrichtigt Go.
  - Go verarbeitet das Ergebnis, ruft C++ für Zusatzwerte auf und verifiziert mit Rust.
- **Linux ELFs**: Unter WSL Ubuntu ausgeführt mit identischen Ergebnissen.

---

## Prüfsumme

- **Datei**: `KR-Compiler-Android-IDE-v4.0.0.apk`
- **SHA-256**: `EA445F39EA7DF69CA1BB1050A1AC93CA5A4F6A7D4E9A01F1CC225FE93791C7B9`
- **Signatur**: Release-Entwicklungsschlüssel (kompatibel mit Vorversionen)
