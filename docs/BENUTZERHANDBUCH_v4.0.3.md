# KR Compiler Android IDE v4.0.3 Benutzerhandbuch

Dieses Handbuch beschreibt den vollständigen Arbeitsablauf von der Erstellung eines neuen Projekts bis zur fertigen Android-APK, Windows-EXE oder Linux-ELF-Datei. 
Version 4.0.3 führt neben der **KR Unified Language (`.kr`)** und der universellen C/C++-Kompatibilität die **vollständige modulare Projektarchitektur (`*.krproject`)** für Enterprise- und Forschungssysteme (z. B. `KR_FORGE`) ein.

---

## 1. Übersicht der Projektarten

KR Compiler Android IDE unterstützt fünf Haupt-Projektkategorien:

| Projektkategorie | Vorlagen / Konfiguration | Dateiformate | Besonderheit |
|---|---|---|---|
| **Modulare KR-Großprojekte** | `*.krproject` (z. B. `forge.krproject`) | `main.kr`, `app/*.kr`, `gui/*.kr`, `research/*.kr`, etc. | **Hierarchische Verzeichnisstruktur**, automatischer Modul-Scan, modulare C++ Translation Units, C++20/Rust/Go FFI-Brücke |
| **KR Unified Language (Single-File)** | KR Konsole, KR GUI (SDL3), Sprachfusion | `main.kr` | C++20, Rust und Go frei in einer Datei kombiniert; automatische FFI-Brücke |
| **C/C++ & Enterprise GPU** | Konsolen- & GUI-Projekte, OpenCL, CMake | `CMakeLists.txt`, `.cpp`, `.c`, `.h` | Clang, CMake, Ninja, vcpkg-Manifest, OpenCL 3.0 Fallback, 100 % Kompatibilität |
| **Rust-Projekte** | Rust Konsole, Rust GUI mit SDL3 | `Cargo.toml`, `src/main.rs` | Standard Cargo- & rustc-Buildpfade |
| **Go-Projekte** | Go Konsole, Go GUI mit SDL3 | `go.mod`, `main.go` | go build mit CGO- und SDL3-Anbindung |

---

## 2. Modulare Großprojekte erstellen & verwalten (`*.krproject`)

Für Großprojekte wie mathematische Plattformen, Spiele-Engines oder verteilte Compute-Systeme empfiehlt sich die modulare Architektur:

### 2.1 Aufbau der Projektkonfigurationsdatei (`*.krproject`)

Platziere eine Konfigurationsdatei mit der Endung `.krproject` im Wurzelverzeichnis deines Projekts (z. B. `forge.krproject`):

```toml
[project]
name = "KR_FORGE"
version = "1.0.0"
entry = "main.kr"
description = "KR_FORGE - Modulare Forschungs- und Berechnungsarchitektur"

[modules]
scan_directories = ["app", "gui", "research", "compute", "orchestration", "storage"]

[dependencies]
packages = ["opencl", "sqlite3", "nlohmann-json"]

[build]
cpp_standard = "c++20"
optimization = "O3"
```

### 2.2 Verzeichnisstruktur (Referenzbeispiel `KR_FORGE`)

```text
KR_FORGE/
├── main.kr                 # Haupteinstiegspunkt mit int main()
├── forge.krproject         # Projekt- & Moduldeklaration
├── app/                    # Anwendungsinitialisierung & Lifecycle (C++)
├── gui/                    # SDL3-Windowing & Widget-Engine
├── research/               # Mathematische Beweis- & Algebra-Engines (Rust)
├── compute/                # Multithreaded CPU- & OpenCL-GPU-Rechenkerne (C++)
├── orchestration/          # Asynchrone Task-Warteschlangen & Dispatcher (Go)
├── storage/                # SQLite-Datenbank & Speicher-Subsystem
├── contracts/              # ABI-Spezifikationen & Datenverträge
├── tests/                  # Cross-Language- & Unit-Tests
└── docs/                   # Architektur- & Entwurfsdokumente
```

### 2.3 Funktionsaufrufe über Modul- und Sprachgrenzen

Module rufen einander direkt auf – der KR-Compiler erzeugt automatisch den zentralen ABI-Header `kr_bridge.h` und verlinkt alle C++-, Rust- und Go-Objekte:

- **Rust in `research/algebra_engine.kr`**:
  ```rust
  pub fn forge_algebra_reduce(a: i64, b: i64) -> i64 {
      if b == 0 { a } else { forge_algebra_reduce(b, a % b) }
  }
  ```

- **Go in `orchestration/job_scheduler.kr`**:
  ```go
  func forge_orchestrate_task(taskId int32, workload int64) bool {
      println("Go Scheduler: Starte Task #", taskId, "mit Workload:", workload)
      return workload > 0
  }
  ```

- **C++ in `app/application.kr`**:
  ```cpp
  #include <iostream>
  #include <cstdint>

  void forge_app_init() {
      bool ok = forge_orchestrate_task(101, 42000);
      int64_t gcd = forge_algebra_reduce(48, 18);
      std::cout << "Initialisierung abgeschlossen: Task=" << (ok ? "OK" : "ERR")
                << ", GCD=" << gcd << std::endl;
  }
  ```

- **C++ in `main.kr`**:
  ```cpp
  #include <iostream>

  int main() {
      std::cout << "=== Starte modulares KR-Projekt ===" << std::endl;
      forge_app_init();
      return 0;
  }
  ```

---

## 3. Kompilierung & Zielsysteme auswählen

1. **Projektordner öffnen**: Öffne den Ordner deines modularen `.kr`-Projekts oder Einzeldatei-Projekts über das Dateisystem der IDE.
2. **Zielsystem wählen**: Wähle in der Funktionsleiste das gewünschte Ziel:
   - **Android ARM64** (`arm64-v8a`): Erzeugt ein direkt installierbares APK-Paket.
   - **Windows x64** (`x86_64-w64-mingw32`): Erzeugt eine native PE/COFF `.exe` oder `.dll`.
   - **Linux x64** (`x86_64-linux-gnu`): Erzeugt eine native ELF-64-Binärdatei.
3. **Kompilieren**: Tippe auf den grünen **Play-/Build-Button**.
   - Die IDE führt den Modul-Scan durch, generiert Translation Units unter `modules/`, baut Rust- und Go-Bibliotheken und linkt das Gesamtsystem fehlerfrei.

---

## 4. Multi-Dependency-Management mit vcpkg

Deklariere benötigte Pakete einfach in der `*.krproject`:
```toml
[dependencies]
packages = ["opencl", "sqlite3", "nlohmann-json"]
```
Der Compiler erzeugt automatisch das passende Manifest `vcpkg.json` und bindet die Header und Import-Bibliotheken der Toolchain für alle drei Zielplattformen ein.

---

## 5. Nutzung des autonomen KI-Programmieragenten („AGENT“)

Ab Version 4.0.3 verfügt die IDE über einen integrierten KI-Programmierassistenten:

### 5.1 Agenten-Dialog öffnen
Tippe in der oberen Navigationsleiste auf den Reiter **`AGENT`** (neben `CODE | DATEIEN | BUILD | ARTEFAKTE`).

### 5.2 Verbindung & Modell konfigurieren
- **HTTPS-Endpunkt**: Trage die URL deines Anbieters ein (z. B. `https://api.openai.com/v1/chat/completions` oder lokal `http://127.0.0.1:11434/v1/chat/completions` für Ollama).
- **Modellname**: Wähle dein bevorzugtes Modell (z. B. `gpt-4o`, `claude-3-5-sonnet`, `llama3.1`, `qwen2.5-coder`).
- **API-Key**: Gib deinen Schlüssel ein (wird sicher im Arbeitsspeicher gehalten und nicht dauerhaft unverschlüsselt gespeichert).

### 5.3 Entwicklungsauftrag erteilen
Beschreibe deine Aufgabe im Auftragsfeld:
- *„Lies mein Projekt, implementiere eine Fehlerbehandlung in `main.kr` und kompiliere für Android ARM64.“*
- *„Finde den Linkerfehler im letzten Build und passe die Signaturen in `kr_bridge.h` an.“*

### 5.4 Genehmigung von Änderungen („Human-in-the-Loop“)
Der Agent arbeitet autonom, aber sicher:
- Vor jedem Schreibzugriff auf Projektdateien (`write`) erscheint ein Dialog mit einer **Vorschau der Änderungen**. Du entscheidest per **Genehmigen** oder **Ablehnen**.
- Vor jedem Kompiliervorgang (`build`) wird ebenfalls eine Bestätigung erbeten.
- Über den Button **`STOPP`** kann der Vorgang jederzeit sofort abgebrochen werden.
