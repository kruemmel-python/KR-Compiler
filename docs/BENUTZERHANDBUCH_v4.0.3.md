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
- Vor jedem Schreibzugriff auf Projektdateien (`write`) oder gezieltem Codeersatz (`replace`) erscheint ein Dialog mit einer **Vorschau der Änderungen**. Du entscheidest per **Genehmigen** oder **Ablehnen**.
- Vor jedem Kompiliervorgang (`build`) wird ebenfalls eine Bestätigung erbeten.
- Über den Button **`STOPP`** kann der Vorgang jederzeit sofort abgebrochen werden.

### 5.5 Lokale KI-Modelle einbinden (LM Studio & Ollama)
Die IDE unterstützt lokale LLMs vollständig ohne Cloud-Zwang oder externe Kosten:
- **LM Studio**:
  - Starte den lokalen Server in LM Studio (z. B. auf Port `1234`).
  - Endpunkt: `http://localhost:1234/v1/chat/completions` (auf dem Desktop) bzw. `http://<PC-IP>:1234/v1/chat/completions` (in der Android-App im selben WLAN).
  - Modellname: Der in LM Studio geladene Modellbezeichner (z. B. `google/gemma-4-e2b`, `qwen2.5-coder-7b`).
  - API-Key: Beliebiger Text (z. B. `lm-studio`).
- **Ollama**:
  - Endpunkt: `http://localhost:11434/v1/chat/completions` bzw. `http://<PC-IP>:11434/v1/chat/completions`.
  - Modellname: Z. B. `codellama`, `llama3.1`, `qwen2.5-coder`.
- **Lange Denkzeiten (10 Minuten Timeout)**:
  - Lokale Modelle auf CPU/iGPU benötigen für komplexe Analysen oft mehrere Minuten. Die IDE gewährt standardmäßig ein Timeout von **600 Sekunden (10 Minuten)** pro Schritt.

### 5.6 Zielgenaues Code-Ersetzen (`replace`)
Statt ganze Dateien neu zu schreiben, nutzt der Agent bevorzugt das `replace`-Werkzeug:
- Ersetzt nur den fehlerhaften Codeabschnitt (`search` $\to$ `replace`).
- Verhindert versehentlichen Codeverlust in großen Dateien.
- Bereinigt automatisch mitkopierte Zeilennummern und toleriert unterschiedliche Einrückungen.

### 5.7 Transparente Quellcode-Zuordnung (`KrSourceMap`)
- Meldet der Compiler einen Fehler in einer generierten Datei (z. B. `KR_BUILD/rust_module/src/lib.rs` oder `modules/gui_window_kr.cpp`), ordnet die IDE den Fehler über die interne `KrSourceMap` automatisch der ursprünglichen `.kr`-Quelldatei zu (z. B. `security/policy.kr`).
- Der Agent liest und editiert stets die echte `.kr`-Projektdatei, nicht das temporäre Build-Artefakt.

### 5.8 Robustes Pfad-Handling & Ordner-Schutz
- **Verzeichnisse werden geschützt**: Fordert der Agent `read -> gui` oder `read -> engine` an, liefert die IDE die Ordnerliste, statt den Ordner als Artefakt auf `main.kr` umzuleiten.
- **Projektweite Suche**: Ruft der Agent `read -> lifecycle.kr` auf, findet die IDE die Datei automatisch unter `app/lifecycle.kr`.
- **Echte Artefakte**: Nur tatsächliche Build-Pfade (`KR_BUILD/`, `src/lib.rs`) werden umgeleitet. Unbekannte Pfade brechen sauber mit einer Fehlermeldung ab.

### 5.9 Sofortiges Anhalten nach erfolgreichem Build
- Sobald das Projekt fehlerfrei gebaut hat (`BUILD OK`) und das Binary (z. B. `programm.exe`) erzeugt wurde, weist die IDE das Modell an, die Aufgabe unverzüglich mit `done` zu beenden.
- Unnötige Nachlauf-Schleifen (mehrfaches Lesen oder unabsichtliches Überschreiben) werden damit effektiv verhindert.

### 5.10 Protokoll-Export & Zwischenablage
- **`📋 Kopieren`**: Überträgt das gesamte Agenten-Protokoll in die Zwischenablage.
- **`💾 Export`**: Speichert das Protokoll als `kr_agent_log.txt` direkt im Projektordner.

---

## 6. Windows Desktop Edition (`KR-Compiler-IDE.jar`)

Mit Version 4.0.3 steht die vollständige Funktionalität der mobilen App auch als native Windows Desktop Edition zur Verfügung:

### 6.1 Starten der Desktop IDE
- Doppelklick auf `windows/Start-KR-IDE.bat` oder Direktausführung von `KR-Compiler-IDE.jar` (erfordert Java 17 oder neuer).
- Für Konsolen-Ausgaben steht `windows/Start-KR-IDE-Console.bat` bereit.

### 6.2 Bedienoberfläche & Arbeitsbereiche
- **Projekt-Explorer (links)**: Zeigt die Projektstruktur an. Neue Dateien und Ordner können per Kontextmenü angelegt werden.
- **Multi-Tab Code-Editor (Mitte)**: Unterstützt Syntax-Highlighting für KR, C++, Rust und Go, automatische Einrückung und Zeilennummern.
- **Build-Konsole (unten)**: Echtzeit-Ausgabe des Compilers (`Clang`, `Cargo`, `Go`, `Linker`) mit farbiger Kennzeichnung.
- **Artefakte-Panel (rechts)**: Zeigt erzeugte Binärdateien (`.exe`, `.apk`, `.elf`) an. Windows-Binärdateien können mit einem Klick direkt gestartet werden.
- **AGENT-Button (oben rechts)**: Öffnet den integrierten KI-Programmierassistenten.

### 6.3 Multi-Target Builds auf Windows
Wähle in der Symbolleiste die Zielplattform:
- **Windows x64 (.exe)**: Erzeugt eine native 64-Bit PE/COFF-Anwendung.
- **Linux x64**: Erzeugt eine statisch gelinkte ELF-64-Binärdatei.
- **Android ARM64 (.apk)**: Erzeugt direkt auf dem Windows-PC eine vollständige, signierte `programm-android-arm64.apk` inklusive aller Bibliotheken (`libuser.so`, `libSDL3.so`, `libc++_shared.so`).
