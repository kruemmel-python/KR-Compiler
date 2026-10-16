# KR Unified Language (.kr) – Entwickler-Handbuch & Sprachfusions-Leitfaden

> Die **KR Unified Language (`.kr`)** ermöglicht es, **C++20, Rust und Go** innerhalb einer einzigen Quelldatei beliebig miteinander zu kombinieren. Es werden **keine künstlichen Sprachblöcke oder Marker** wie `@cpp`, `@rust`, `@go` oder `language="..."` benötigt. Der KR-Compiler analysiert den Quelltext strukturell, teilt ihn in Sprachsegmente auf, generiert automatisch eine C-kompatible ABI-Brücke und erzeugt ein einzelnes, hochoptimiertes ausführbares Programm für Android (ARM64), Windows (x64) und Linux (x64).

---

## 1. Grundprinzip: Wie funktioniert die Sprachfusion?

In einer `.kr`-Datei schreibt der Entwickler Code einfach in der Syntax der gewünschten Sprache. Der Compiler erkennt den Sprachwechsel anhand syntaktischer Konstrukte auf Deklarations- und Definitionsebene:

- **C++20-Bereiche**: `#include`, Klassen, Templates, Operatoren, Namespaces und reguläre C/C++-Funktionsdefinitionen.
- **Rust-Bereiche**: `fn`, `pub fn`, `let`, `mut`, `match`, `impl`, `trait`, Rust-Makros (`println!`) und Rust-Typen (`i32`, `f64`, etc.).
- **Go-Bereiche**: `func`, `package`, `import`, Go-Typen (`int32`, `float64`), Go-Routinen und Channels.

```mermaid
flowchart TD
    subgraph SingleSource ["Einzige Quelldatei: main.kr"]
        CPP1["C++: Tensoren & Kernel"]
        RUST1["Rust: Mathematische RoPE-Formel"]
        GO1["Go: Fehlertoleranz & Prüfsummen"]
        CPP2["C++: main() Programmeinstieg"]
    end

    SingleSource --> Pipeline["KR Frontend Pipeline (Lexer, Parser, Symboltabelle)"]
    
    Pipeline --> GenCPP["Generiertes C++ Modul"]
    Pipeline --> GenRust["Generiertes Rust Modul (Cargo/lib.rs)"]
    Pipeline --> GenGo["Generiertes Go Modul (Cgo/main.go)"]

    GenCPP --> Clang["Clang++ (C++20)"]
    GenRust --> Rustc["rustc / Cargo"]
    GenGo --> GoBuild["go build (c-shared / c-archive)"]

    Clang --> Linker["KR Linker & Finalizer"]
    Rustc --> Linker
    GoBuild --> Linker

    Linker --> Android["Android APK (ARM64)"]
    Linker --> Windows["Windows EXE (x64)"]
    Linker --> Linux["Linux ELF (x64)"]
```

---

## 2. Die Rollenverteilung: Welche Sprache wofür?

Ein erfahrener Entwickler nutzt die Stärken jeder Sprache gezielt aus:

| Sprache | Idealer Einsatzbereich in `.kr` | Stärken |
|---|---|---|
| **C++20** | Tensoren, Vektoren, Speicherpuffer, SDL3-Grafik, Pointerarithmetik | Maximale Cache-Lokalität, SIMD/Auto-Vektorisierung, RAII |
| **Rust** | Mathematische Kernalgorithmen, Validierung, Zustandsmaschinen | Mathematische Korrektheit, Speichersicherheit, keine Data-Races |
| **Go** | I/O-Orchestrierung, Nebenläufigkeit, Logging, Schwellenwert-Prüfung | Schnelle Concurrency, robuste Standardbibliothek, einfache Syntax |

---

## 3. Kreuzaufrufe (Cross-Language Calls)

Funktionen können sich **über alle Sprachgrenzen hinweg** in allen 6 Richtungen direkt aufrufen:

```mermaid
flowchart LR
    CPP["C++20"] <-->|Direktaufruf| RUST["Rust"]
    RUST <-->|Direktaufruf| GO["Go"]
    GO <-->|Direktaufruf| CPP
```

### Die 3 goldenen Regeln für Funktionsaufrufe:

1. **Globale Eindeutigkeit von Funktionsnamen**:
   Zwei Funktionen in unterschiedlichen Sprachen dürfen nicht denselben Namen tragen.
   - *Richtig*: `cpp_calculate()`, `rust_verify()`, `go_notify()`
   - *Falsch*: `calculate()` in C++ und gleichzeitig `calculate()` in Rust definiert (führt zur Symbolkollision).

2. **C-kompatible Parameter- und Rückgabetypen verwenden**:
   Alle Typen, die Sprachgrenzen überschreiten, müssen im gemeinsamen KR-Typsystem darstellbar sein (siehe Abschnitt 4).

3. **Keine manuellen FFI-Definitionen schreiben**:
   Du musst **kein** `extern "C"`, kein `#[no_mangle]` und kein `//export` schreiben. Der KR-Compiler erkennt anhand der Aufrufbeziehungen selbst, welche Funktionen exportiert werden müssen, und generiert die passenden Wrapper automatisch.

---

## 4. Das gemeinsame Typsystem & ABI-Kompatibilität

Damit Werte verlustfrei und ohne Abstürze zwischen C++, Rust und Go fließen, werden alle sprachübergreifenden Typen auf eine stabile C-kompatible ABI abgebildet:

### 4.1 Skalare Typen-Matrix

| Datentyp | C++20 | Rust | Go | ABI-Repräsentation |
|---|---|---|---|---|
| **Boolean** | `bool` | `bool` | `bool` | `uint8_t` (0 oder 1) |
| **8-Bit Integer (signed)** | `int8_t` / `char` | `i8` | `int8` | `int8_t` |
| **8-Bit Integer (unsigned)**| `uint8_t` / `unsigned char` | `u8` | `uint8` | `uint8_t` |
| **16-Bit Integer (signed)** | `int16_t` / `short` | `i16` | `int16` | `int16_t` |
| **16-Bit Integer (unsigned)**| `uint16_t` | `u16` | `uint16` | `uint16_t` |
| **32-Bit Integer (signed)** | `int32_t` / `int` | `i32` | `int32` | `int32_t` |
| **32-Bit Integer (unsigned)**| `uint32_t` / `unsigned int` | `u32` | `uint32` | `uint32_t` |
| **64-Bit Integer (signed)** | `int64_t` / `long long` | `i64` | `int64` | `int64_t` |
| **64-Bit Integer (unsigned)**| `uint64_t` | `u64` | `uint64` | `uint64_t` |
| **32-Bit Float** | `float` | `f32` | `float32` | `float` (IEEE 754) |
| **64-Bit Float** | `double` | `f64` | `float64` | `double` (IEEE 754) |
| **Kein Rückgabewert** | `void` | `()` (oder weglassen) | (weglassen) | `void` |

Verwende in C++ am besten die Standard-Typen aus `<cstdint>` (`int32_t`, `uint64_t`, `double`). Das garantiert auf allen 3 Zielplattformen (Android ARM64, Windows x64, Linux x64) exakt übereinstimmende Bitbreiten.

---

### 4.2 Zeiger, Arrays und Speicherverwaltung (Ownership)

Wenn große Datenmengen (z. B. Audio-Puffer, Tensoren oder Bildmatrizen) verarbeitet werden, übergibt man Zeiger (`*const T` bzw. `const T*`). Dabei gilt die verbindliche **Ownership-Regel**:

**Wer allokiert, der gibt frei!**
- Wenn C++ einen `std::vector<double>` allokiert, bleibt der Speicher im Besitz von C++. Rust und Go dürfen lesend (`const double*`) oder inplace modifizierend (`double*`) darauf zugreifen, dürfen den Speicher aber **niemals** eigenständig freigeben (kein `free()` in Go/C, kein `drop` in Rust).
- Go-Garbage-Collector-Zeiger dürfen **nicht** an C++ übergeben werden, um dort dauerhaft in globalen Strukturen gespeichert zu werden, da der Go-GC Objekte verschieben oder freigeben kann.
- C++-Klassen mit nicht-trivialen Destruktoren (z. B. `std::string`, `std::vector`) dürfen **nicht by-value** über Sprachgrenzen gereicht werden. Stattdessen übergibt man den Datenzeiger (`vec.data()`) und die Länge (`vec.size()`).

---

## 5. Vollständiges Praxisbeispiel: RoPE Attention

Dieses Beispiel demonstriert eine rechenintensive Sprachfusion aus der Praxis:
- **C++**: Verwaltet die 3D-Tensoren und den hochoptimierten Attention-Kernel.
- **Rust**: Berechnet die RoPE-Rotationsfrequenzen (`base.powf(...)`).
- **Go**: Führt die numerische Fehlerakzeptanz- und Schwellenwertprüfung durch.

```cpp
#include <iostream>
#include <vector>
#include <cmath>
#include <cstdint>

// ---------------------------------------------------------------------------
// 1. C++ Bereich: Tensor-Datenstrukturen
// ---------------------------------------------------------------------------
struct Tensor {
    std::size_t size;
    std::vector<double> data;
    explicit Tensor(std::size_t n) : size(n), data(n, 0.0) {}
};

// C++ ruft die unten definierte Rust-Funktion 'rust_rope_frequency' auf.
// Es ist KEINE manuelle Header-Deklaration nötig!
void apply_rope_to_tensor(Tensor& t, double base) {
    for (std::size_t i = 0; i < t.size; ++i) {
        double freq = rust_rope_frequency(static_cast<int32_t>(i), static_cast<int32_t>(t.size), base);
        t.data[i] *= std::cos(freq);
    }
}

// ---------------------------------------------------------------------------
// 2. Rust Bereich: Reine mathematische Formel
// ---------------------------------------------------------------------------
fn rust_rope_frequency(index: i32, width: i32, base: f64) -> f64 {
    base.powf(-2.0 * (index as f64) / (width as f64))
}

// ---------------------------------------------------------------------------
// 3. Go Bereich: Validierung & Logging
// ---------------------------------------------------------------------------
func go_verify_result(value float64, threshold float64) bool {
    isValid := value >= 0.0 && value <= threshold
    if isValid {
        println("Go: Validierung erfolgreich fuer Wert =", value)
    } else {
        println("Go: FEHLER - Schwellenwert ueberschritten!")
    }
    return isValid
}

// ---------------------------------------------------------------------------
// 4. Gemeinsamer Einstiegspunkt (C++)
// ---------------------------------------------------------------------------
int main() {
    std::cout << "=== KR Unified Language: RoPE Attention ===" << std::endl;
    Tensor q(8);
    for (std::size_t i = 0; i < q.size; ++i) q.data[i] = 1.0;

    apply_rope_to_tensor(q, 10000.0);

    // C++ ruft die Go-Validierungsfunktion auf:
    bool ok = go_verify_result(q.data[0], 1.5);
    std::cout << "Ergebnis-Status: " << (ok ? "OK" : "FEHLER") << std::endl;
    return ok ? 0 : 1;
}
```

---

## 6. Häufige Fehlerquellen & Fallstricke (Troubleshooting)

### 1. `use of undeclared identifier '...'`
- **Ursache**: Der Name einer Funktion wurde in der aufrufenden Sprache falsch geschrieben, oder eine der beteiligten Funktionen hat inkompatible Typen (z. B. ein C++-Objekt by-value übergeben).
- **Lösung**: Überprüfe die Schreibweise und stelle sicher, dass Parameter als primitive Typen (`int32_t`, `double`, Rohzeiger) übergeben werden.

### 2. Mehrfache `main()`-Funktionen
- **Ursache**: In Go wurde `func main() {}` geschrieben, während in C++ bereits `int main()` existiert.
- **Regel**: **Nur ein einziger Programmeinstieg pro `.kr`-Datei!** Wenn C++ `int main()` hat, schreibe in Go **kein** `func main()`. Der KR-Compiler erzeugt für Go und Rust im Hintergrund automatisch die nötigen Modul-Einstiege.

### 3. C++-Exceptions über FFI-Grenzen
- **Ursache**: Eine C++-Funktion wirft eine `std::exception`, während sie von Rust oder Go aus aufgerufen wurde.
- **Regel**: C++-Exceptions können nicht über C-ABI-Grenzen geworfen werden (führt zu `std::terminate`). Fange Exceptions innerhalb der C++-Funktion mit `try/catch` ab und gib stattdessen einen Fehlercode (`int32_t` oder `bool`) an Rust/Go zurück.

### 4. Panics in Rust
- **Ursache**: Ein `panic!` in Rust bricht das gesamte Programm ab.
- **Regel**: Verwende in Rust-Bereichen, die von C++ oder Go aufgerufen werden, defensive Prüfungen und gib Fehlerwerte zurück, statt `panic!` oder `.unwrap()` aufzurufen.

---

## 7. Zusammenfassung: Checkliste für Einzeldatei-Projekte

- [x] **Sprachwechsel**: Schreibe Code einfach in C++, Rust oder Go.
- [x] **Dateiendung**: Speichere das Projekt als `main.kr`.
- [x] **Typen**: Verwende feste Skalartypen (`int32_t`/`i32`/`int32`, `double`/`f64`/`float64`).
- [x] **Pointer**: Übergib Puffer als Rohzeiger (`const double*`, `len: usize`).
- [x] **Einstiegspunkt**: Definiere genau ein `main()` (typischerweise in C++).
- [x] **Build**: Klicke in der KR Compiler Android IDE auf **Kompilieren** – die IDE baut automatisch für deine Zielplattform (Android ARM64, Windows x64 oder Linux x64).

---

## 8. Modulare Großprojekte mit `*.krproject` & Multi-File KR-Architektur

Für komplexe Enterprise-, Forschungs- und Simulationssysteme reicht eine einzelne Datei nicht aus. Die KR Compiler Android IDE unterstützt ab Version 4.0.3 **vollständige modulare Projektstrukturen** mit hierarchischen Unterverzeichnissen, automatischer Modulerkennung und projektweiter Cross-Language-Auflösung.

```mermaid
flowchart TD
    subgraph ModularRoot ["Projektwurzel (z. B. KR_FORGE/)"]
        PROJ["forge.krproject (TOML-Projektkonfiguration)"]
        MAIN["main.kr (Einstiegspunkt & Koordination)"]
    end

    subgraph Submodules ["Modul-Verzeichnisse (*.kr)"]
        APP["app/ (application.kr, lifecycle.kr)"]
        GUI["gui/ (window_system.kr, widget_engine.kr)"]
        RESEARCH["research/ (mathematical_ir.kr, algebra_engine.kr)"]
        COMPUTE["compute/ (cpu_executor.kr, opencl_executor.kr)"]
        ORCH["orchestration/ (job_scheduler.kr, worker_runtime.kr)"]
        STORAGE["storage/ (database.kr, artifact_store.kr)"]
    end

    ModularRoot --> Pipeline["KR Modular Pipeline"]
    Submodules --> Pipeline

    Pipeline --> KrBridge["Zentraler ABI-Header: kr_bridge.h"]
    Pipeline --> ModularCPP["Modulare C++ Translation Units (modules/*.cpp)"]
    Pipeline --> AggRust["Rust Crate (kr_rust/src/lib.rs)"]
    Pipeline --> AggGo["Go Module (kr_go/main.go)"]

    ModularCPP --> Linker["KR Finalizer & Linker"]
    AggRust --> Linker
    AggGo --> Linker

    Linker --> Artifacts["Android APK / Windows EXE / Linux ELF"]
```

---

### 8.1 Die Projektkonfiguration (`*.krproject`)

Ein modulares Projekt wird durch eine Konfigurationsdatei mit der Endung `.krproject` (z. B. `forge.krproject`) gesteuert. Die Syntax ist an TOML angelehnt und deklariert Metadaten, Scan-Pfade und Abhängigkeiten:

```toml
[project]
name = "KR_FORGE"
version = "1.0.0"
entry = "main.kr"
description = "KR_FORGE - Modular Mathematical Research and Algebraic Verification Platform"

[modules]
scan_directories = ["app", "gui", "research", "compute", "orchestration", "storage"]

[dependencies]
packages = ["opencl", "sqlite3", "nlohmann-json"]

[build]
cpp_standard = "c++20"
optimization = "O3"
```

#### Bedeutung der Konfigurationssektionen:

1. **`[project]`**:
   - `name`: Eindeutiger Projektname.
   - `version`: Semantische Versionierung des Projekts.
   - `entry`: Relativer Pfad zur Haupteinstiegsdatei (standardmäßig `main.kr`).
   - `description`: Beschreibung des Systems für die IDE und das Build-Log.

2. **`[modules]`**:
   - `scan_directories`: Liste von Ordnern relativ zur Projektwurzel, die rekursiv nach `.kr`-Dateien durchsucht werden.
   - Jede gefundene `.kr`-Datei wird als Modul kompiliert und in den Namensraum eingebunden.

3. **`[dependencies]`**:
   - `packages`: Liste benötigter C/C++-Pakete (z. B. `opencl`, `sqlite3`, `nlohmann-json`).
   - Der Compiler erzeugt daraus automatisch ein valides `vcpkg.json` für den Sysroot- und Paketmanager-Build.

4. **`[build]`**:
   - `cpp_standard`: Verwendeter C++-Standard (`c++17`, `c++20`, `c++23`).
   - `optimization`: Compiler-Optimierungsstufe (`O0`, `O2`, `O3`, `Os`).

---

### 8.2 Kanonische Enterprise-Verzeichnisstruktur (`KR_FORGE`)

Das folgende Referenzlayout demonstriert die empfohlene modulare Ordnerstruktur:

```text
KR_FORGE/
│
├── main.kr                          # Programmeinstieg & Modulorchestrierung
├── forge.krproject                  # Projekt- und Abhängigkeitskonfiguration
│
├── app/
│   ├── application.kr               # Anwendungsinitialisierung & Event-Schleife (C++)
│   ├── lifecycle.kr                 # Zustandsübergänge & Ressourcenverwaltung
│   └── configuration.kr             # Laufzeitkonfiguration & CLI-Parameter
│
├── gui/
│   ├── window_system.kr             # SDL3-Fensterverwaltung & High-DPI-Skalierung
│   ├── widget_engine.kr             # UI-Komponenten, Buttons, Textfelder
│   ├── dock_manager.kr              # Andockbare Panels & Layout-Persistenz
│   ├── graph_renderer.kr            # Hardwarebeschleunigtes Rendering mathematischer Graphen
│   ├── formula_editor.kr            # Formeleingabe & LaTeX/Unicode-Editor
│   ├── discovery_view.kr            # Interaktive Ansicht für mathematische Hypothesen
│   ├── proof_view.kr                # Formaler Beweisbaum & Schrittvalidierung
│   └── compute_view.kr              # Performance-Graphen & Hardware-Auslastung
│
├── research/
│   ├── mathematical_ir.kr           # Intermediäre Repräsentation algebraischer Ausdrücke
│   ├── algebra_engine.kr            # Symbolische Termersetzung & Vereinfachung (Rust)
│   ├── structural_search.kr         # Mustererkennung in Ausdrucksbäumen
│   ├── equivalence_engine.kr        # Beweis formaler Äquivalenz
│   ├── synthesis_engine.kr          # Heuristische Synthese optimaler Formeln
│   ├── verification_engine.kr       # Formale Verifikation gegen Axiomensysteme
│   └── strategy_evolution.kr        # Evolutionäre Suchstrategien & Optimierung
│
├── compute/
│   ├── cpu_executor.kr              # Multithreaded SIMD/AVX/NEON CPU-Rechenkern (C++)
│   ├── opencl_executor.kr           # OpenCL-Kernel-Dispatch & GPU-Pipeline
│   ├── code_generator.kr            # JIT-/AOT-Kompilierung validierter Formeln
│   ├── benchmark_engine.kr          # Nanosekunden-genaue Zeitmessung & FLOPS-Zähler
│   └── memory_manager.kr            # Cache-lokale Puffer- und Tensorallokation
│
├── orchestration/
│   ├── job_scheduler.kr             # Asynchrone Task-Warteschlange (Go)
│   ├── experiment_manager.kr        # Verwaltung von Parameterstudien & Läufen
│   ├── worker_runtime.kr            # Hintergrund-Threads & Worker-Pools
│   └── event_dispatch.kr            # Ereignisbus für Subsystem-Kommunikation
│
├── storage/
│   ├── database.kr                  # SQLite-Speicherung von Entdeckungen & Metadaten
│   ├── artifact_store.kr            # Speicherung kompilierter Binär- & Beweisartefakte
│   ├── provenance.kr                # Mathematische Herkunft & Versionshistorie
│   └── snapshots.kr                 # Sicherungspunkte & Wiederherstellung
│
├── contracts/
│   ├── abi_contract.md              # Spezifikation der sprachübergreifenden ABI
│   ├── data_schema.md               # Datenformate für Beweise und Graphen
│   └── status_codes.md              # Einheitliche Rückgabecodes aller Schichten
│
├── tests/
│   ├── cross_language/              # E2E-Tests über Sprach- und Modulgrenzen
│   ├── mathematics/                 # Unit-Tests für algebraische Korrektheit
│   ├── verification/                # Verifikationstests gegen mathematische Theoreme
│   ├── benchmarks/                  # FLOPS- und Latenzbenchmarks
│   └── integration/                 # Gesamtsystemtests
│
└── docs/
    ├── ARCHITECTURE.md              # Schichtenarchitektur & Datenfluss
    ├── RESEARCH_METHOD.md           # Wissenschaftliche Methodik & Heuristiken
    ├── GUI_SPECIFICATION.md         # UI- und Interaktionsrichtlinien
    └── MATHEMATICAL_PROVENANCE.md   # Nachvollziehbarkeitsmodell für Beweise
```

---

### 8.3 Cross-File & Cross-Language Modulaufrufe

In modularen `.kr`-Projekten können Funktionen **über Dateigrenzen hinweg** aufgerufen werden, ohne dass manuelle Header-Inkludierungen (`#include`) oder FFI-Importe erforderlich sind.

#### Beispiel: Aufrufbeziehung im KR_FORGE-System

1. **`research/algebra_engine.kr` (Rust-Segment)**:
```rust
pub fn forge_algebra_reduce(a: i64, b: i64) -> i64 {
    if b == 0 {
        a
    } else {
        forge_algebra_reduce(b, a % b)
    }
}
```

2. **`orchestration/job_scheduler.kr` (Go-Segment)**:
```go
func forge_orchestrate_task(taskId int32, workload int64) bool {
    println("Go Scheduler: Starte Task #", taskId, "mit Workload:", workload)
    return workload > 0
}
```

3. **`app/application.kr` (C++-Segment)**:
```cpp
#include <iostream>
#include <cstdint>

void forge_app_init() {
    std::cout << "[KR_FORGE] Initialisiere Core-Subsysteme..." << std::endl;

    // Ruft die Go-Funktion aus orchestration/job_scheduler.kr auf:
    bool taskOk = forge_orchestrate_task(101, 42000);

    // Ruft die Rust-Funktion aus research/algebra_engine.kr auf:
    int64_t gcd = forge_algebra_reduce(48, 18);

    std::cout << "[KR_FORGE] Subsystem-Status: Task=" << (taskOk ? "OK" : "ERR")
              << ", GCD(48, 18)=" << gcd << std::endl;
}
```

4. **`main.kr` (Haupteinstieg)**:
```cpp
#include <iostream>

void forge_app_init(); // Deklaration optional, Compiler bindet Module automatisch

int main() {
    std::cout << "=== Start von KR_FORGE ===" << std::endl;
    forge_app_init();
    std::cout << "=== KR_FORGE erfolgreich beendet ===" << std::endl;
    return 0;
}
```

---

### 8.4 Wie der KR-Compiler modulare Projekte verarbeitet

Wenn ein modulares Projekt mit `.krproject` kompiliert wird, führt die Compiler-Engine folgende Schritte automatisiert durch:

1. **Projekt-Erkennung**:
   - Erkennt das Vorhandensein von `*.krproject` im gewählten Projektordner.
   - Parst die deklarierten Scan-Verzeichnisse (`[modules]`).

2. **Relativer Modul-Scan**:
   - Findet alle `.kr`-Dateien in den Scan-Verzeichnissen.
   - Behält relative Pfade bei (z. B. `app/application.kr`, `gui/window_system.kr`), um Namenskollisionen zu verhindern.

3. **Inkrementelle Source-Generierung**:
   - **`kr_bridge.h`**: Zentraler ABI-Header mit allen Funktionsprototypen aus allen Modulen.
   - **`modules/*.cpp`**: Jede modulare `.kr`-Datei mit C++-Inhalten erhält ihre eigene Translation Unit (z. B. `modules/app_application_kr.cpp`).
   - **`kr_rust/src/lib.rs`**: Sammelt alle Rust-Funktionen modulübergreifend in einem zusammenhängenden Crate.
   - **`kr_go/main.go`**: Sammelt alle Go-Routinen modulübergreifend in einem Cgo-kompatiblen Package.

4. **Kompilierung & Linking**:
   - Clang++ kompiliert alle modularen C++-Dateien parallel (`-c modules/*.cpp -o modules/*.o`).
   - Cargo/Rustc baut die Rust-Komponenten als statische Bibliothek `libkr_rust.a`.
   - Go baut die Go-Komponenten via `cgo` als Archiv `libkr_go.a`.
   - Der LLD-Linker verlinkt alle C++-Objekte, die Rust- und Go-Bibliotheken sowie die Systembibliotheken zum Zielprodukt (**Android APK**, **Windows EXE** oder **Linux ELF**).

---

### 8.5 Checkliste für modulare Großprojekte

- [x] **Projektdatei anlegen**: Platziere eine `*.krproject`-Datei im Wurzelverzeichnis.
- [x] **Scan-Verzeichnisse angeben**: Trage alle Modulordner in `[modules] scan_directories` ein.
- [x] **Einstiegspunkt festlegen**: Bestimme die Datei mit `main()` (z. B. `main.kr`).
- [x] **Eindeutige Funktionsnamen**: Verwende aussagekräftige Präfixe für exportierte Funktionen (`modul_sprache_name`).
- [x] **Abhängigkeiten deklarieren**: Nutze `[dependencies] packages = [...]` für externe C/C++-Bibliotheken via vcpkg.
- [x] **Kompilieren**: Wähle in der IDE das Zielsystem (Android ARM64, Windows x64, Linux x64) und starte den Build.
