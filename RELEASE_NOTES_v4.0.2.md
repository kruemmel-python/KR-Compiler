# KR Compiler Android IDE v4.0.2 – Release Notes

Version 4.0.2 ist das umfassende C/C++- und CMake-Sysroot-Kompatibilitäts-Release für KR Compiler Android IDE.
Es behebt alle Hürden bei realen GitHub-C++-Repositories und ermöglicht das **sofortige, unveränderte Kompilieren** moderner C++17/C++20- und CMake-Codebases direkt auf Android für alle drei Zielplattformen: **Android ARM64**, **Windows x64** und **Linux x64**.

---

## Neuerungen & Systemische Behebungen in Version 4.0.2

### 1. 100 % Erfolgsquote an 18 realen C/C++ GitHub-Repositories (54 Builds)
Alle 18 getesteten C/C++-Repositories aus der Praxis kompilieren nun out-of-the-box ohne manuelle Anpassung im Repository:
1. `MDraft` – Markdown Editor mit GUI & Highlighting (**Android APK**, **Windows EXE**, **Linux ELF**)
2. `Tatarus-6` – Enterprise C++ Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
3. `Tatarus-5` – High-Performance Matrix & Neural Core (**Android APK**, **Windows EXE**, **Linux ELF**)
4. `Tatarus-4` – C++ Core Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
5. `mycelia_Craft` – C++ Voxel & Simulation Framework (**Android APK**, **Windows EXE**, **Linux ELF**)
6. `TATARUS-Neural-Identity` – AI Identity & Cryptographic Security Core (**Android APK**, **Windows EXE**, **Linux ELF**)
7. `SovereignComputing` – Dezentrale Compute Nodes & Crypto Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
8. `CMS-MyceliaDB-Enterprise` – Embedded Enterprise Storage & Database Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
9. `Mycelia-Web-CMS` – Asynchrones C++ Web- & API-Backend (**Android APK**, **Windows EXE**, **Linux ELF**)
10. `Chroniken-der-Gegenwelt` – Game Engine & Story World Framework (**Android APK**, **Windows EXE**, **Linux ELF**)
11. `KALYX` – Neural Knowledge & Graph Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
12. `KALYX-KIE` – Knowledge Inference Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
13. `Keim-Genesis` – Evolutionäre Algorithmen & Bio-Simulation (**Android APK**, **Windows EXE**, **Linux ELF**)
14. `llm_ccq4` – Quantized LLM Inference Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
15. `pgx_Engine` – PostgreSQL / Graph Accelerator Engine (**Android APK**, **Windows EXE**, **Linux ELF**)
16. `AI-Shield` – Real-Time AI Security & AMSI Guard (**Android APK**, **Windows EXE**, **Linux ELF**)
17. `LyricsStudio` – Win32 High-DPI Audio & Lyrics Studio (**Android APK**, **Windows EXE**, **Linux ELF**)
18. `Algorithmic-Genesis-Enterprise-Research-Platform` – Enterprise OpenCL & SQLite Platform (**Android APK**, **Windows EXE**, **Linux ELF**)

---

### 2. Kernverbesserungen der Toolchain & CMake-Engine
- **Intelligente Projektstruktur-Erkennung**: Automatische Auffindung von `CMakeLists.txt` in verschachtelten Unterverzeichnissen (`findPrimaryCMakeProject`).
- **CMake Toolchain & Flag-Sanitierung**:
  - Filterung destruktiver Compiler- und Linkerflags (`-Werror`, `/WX`, `-fstack-protector*`, `-Wl,-z,*`).
  - Automatische Konfiguration von Threading-Primitiven (`Threads_FOUND`, `CMAKE_HAVE_LIBC_PTHREAD`, `CMAKE_THREAD_LIBS_INIT`).
  - Vorkonfiguration von System-Headern (`OpenCL`, `SQLite3`, `X11`) zur Vermeidung von Fehlkonfigurationen durch Third-Party-Find-Module.
- **Header-Pfade & Libc++-Schutz**:
  - Trennung und Priorisierung von `usr/include/c++/v1` vor C-Systemheadern zur Verhinderung fehlerhafter `#include_next`-Ketten.
  - Harmonisierung der Pfadaliase `/data/user/0/kr.compile` und `/data/data/kr.compile` in `CMAKE_C_IMPLICIT_INCLUDE_DIRECTORIES` und `CMAKE_CXX_IMPLICIT_INCLUDE_DIRECTORIES`.
- **GCC-14 libstdc++ Header-Patch**:
  - Behebung des GCC-14 `std::pair` Standard-Konstruktor-Bugs bei unvollständigen rekursiven Typen (z. B. `JsonValue` in `Tatarus-4/5/6`) unter Clang.
- **Windows-Cross-Kompilierung & Win32 API Modernisierung**:
  - Standardmäßige Anhebung von `_WIN32_WINNT` auf `0x0A00` (Windows 10/11) in MinGW-Headern für moderne DPI- und Windowing-APIs (`SetProcessDpiAwarenessContext`, `GetDpiForWindow`).
  - Automatische Entry-Point-Erkennung für Unicode-Programme (`wWinMain` / `wmain` -> `-municode`).
  - Entfernung nicht cross-kompilierbarer Windows-Ressourcendateien (`.rc`) im Linux-/Android-Modus.
- **Direkt-Build-Engine**:
  - Rekursive Header-Inkludierung, Unterstützung für `-std=gnu17` und `-std=c++20`.
  - Getrennte Objekt-Kompilierung bei gemischten C- und C++-Projekten.
  - Automatischer Shared-Library-Modus (`.so` / `.dll`), falls kein `main()` vorhanden ist.
- **Speicher- und SAF-Optimierung**:
  - Ausschluss unnötiger Build- und Abhängigkeitsordner (`build`, `build_vs`, `node_modules`, `cmake-build-*`) beim Projektimport via Android Storage Access Framework (SAF).

---

## Prüfsumme

- **Datei**: `KR-Compiler-Android-IDE-v4.0.2.apk`
- **Größe**: `743.994.388 Bytes`
- **SHA-256**: `B22B6035425CCA7F1539B507E99F66673E2E0B9610E90371256B33D28A25DE51`
- **Signatur**: Release-Entwicklungsschlüssel (v4.0-kompatibel)
