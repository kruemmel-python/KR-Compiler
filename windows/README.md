# KR Compiler IDE – Windows Desktop Edition (v4.1.1)

**Entwickler:** Ralf Krümmel  
**Version:** 4.1.1
**Plattform:** Windows x64 (10 / 11)  
**Technologie:** Java 17+ (Swing Native Dark UI), Clang/LLVM, Rust (cargo/rustc), Go, CMake, Ninja, vcpkg  

---

## 🌟 Übersicht

Die **KR Compiler IDE Windows Desktop Edition** ist die direkte 1-zu-1-Desktop-Implementierung der mobilen Android-Entwicklungsumgebung. Sämtliche Kernmodule – von der **KR Unified Language Multi-File Pipeline** über den **autonomen KI-Programmierassistenten („AGENT“)** bis hin zur **Multi-Target-Kompilierung** – wurden vollständig nativ für Windows portiert und lauffähig bereitgestellt.

---

## 🚀 Hauptfunktionen (1 zu 1 mit der mobilen IDE)

### 1. Navigation & Ansichten
- **`CODE`**:
  - Moderner Multi-Tab-Editor mit Syntax-Highlighting für KR Unified Language (`.kr`), C++20 (`.cpp`, `.h`), Rust (`.rs`), Go (`.go`), TOML (`.krproject`, `Cargo.toml`) und CMake (`CMakeLists.txt`).
  - Zeilennummerierung, Echtzeit-Bearbeitungsanzeige (`*`), Statuszeile mit Zeilen-, Spalten- und Zeichenzähler.
  - Tastatur-Shortcuts: `Strg+S` (Speichern), `Strg+Umschalt+S` (Alles speichern), `Strg+N` (Neues Projekt), `Strg+O` (Projekt öffnen), `F5` (Kompilieren), `Strg+A` (KI-Agent).
- **`DATEIEN`**:
  - Interaktiver Projekt-Dateibaum mit Rechtsklick-Kontextmenü (Neue Datei, Neuer Ordner, Umbenennen, Löschen, Im Windows Explorer öffnen).
  - Sofortige Dateisystem-Synchronisation.
- **`BUILD`**:
  - Plattformauswahl: **Windows x64 (.exe)**, **Linux x64 (ELF)**, **Android ARM64 (.so)**.
  - Live-Compiler-Konsole mit detailliertem Protokoll und Fehlerdiagnostik.
  - Optionale vcpkg Online-Abhängigkeiten.
- **`ARTEFAKTE`**:
  - Übersicht aller erzeugten Binärdateien und Logs im Ordner `KR_BUILD/`.
  - **1-Klick-Ausführung (`▶️ Ausführen`)**: Startet die kompilierte `.exe` direkt in einem separaten Windows-Befehlsfenster.
  - „Im Explorer anzeigen“ und „Log im Editor öffnen“.
- **`🤖 AGENT`**:
  - Integrierter autonomer KI-Programmieragent mit ReAct-Werkzeugschleife (`list`, `read`, `write`, `build`, `done`).
  - Volle Unterstützung für alle OpenAI-kompatiblen HTTPS-APIs sowie lokale Modelle über **Ollama** (`http://127.0.0.1:11434`) oder **LM Studio** (`http://127.0.0.1:1234`).
  - **Sicherheits-Sandbox & Genehmigungssystem**: Human-in-the-Loop-Dialoge vor Dateiänderungen und Build-Starts.
  - **Revisionssicheres Audit-Log**: Protokollierung in `kr_agent_audit.jsonl`.
  - **`📋 Kopieren`**: Ein-Klick-Kopieren des gesamten Verlaufs in die Windows-Zwischenablage.
  - **`💾 Export`**: Speichert das Protokoll formatiert als `kr_agent_log.txt` im Projektordner.
  - Live-Statusbanner und ProgressBar; der Agent läuft auch bei minimiertem Fenster ununterbrochen weiter.

---

### 2. Projekt-Vorlagen & KR-Architektur

Über das Menü **`Projekt -> Neues Projekt erstellen …`** können Sie folgende Vorlagen sofort generieren:
1. **`MODULAR_KR_FORGE`**: Vollständiges modulares Referenzprojekt mit `forge.krproject`, `main.kr`, `app/`, `gui/`, `research/`, `compute/`, `orchestration/`, `storage/`.
2. **`SINGLE_KR`**: Einzeldatei-KR-Programm (`main.kr`).
3. **`CPP_PROJECT`**: C++20-Projekt mit `CMakeLists.txt` und `main.cpp`.
4. **`RUST_PROJECT`**: Rust-Projekt mit `Cargo.toml` und `src/main.rs`.
5. **`GO_PROJECT`**: Go-Projekt mit `go.mod` und `main.go`.

---

## 📦 Schnellstart

### Ausführen
Doppelklicken Sie im Ordner `windows\` auf:
- **`Start-KR-IDE.bat`**: Startet die IDE lautlos als reine Desktop-Anwendung.
- **`Start-KR-IDE-Console.bat`**: Startet die IDE mit sichtbarem Terminal-Fenster (für Entwickler & Debugging).

Die Distribution enthält ihre eigene Java-Laufzeit unter `runtime\jdk` und lädt
beim Start weder JDK noch Compiler nach. Alternativ direkt per Befehlszeile:
```cmd
runtime\jdk\bin\java.exe -jar dist\KR-Compiler-IDE.jar
```

### Selbst neu kompilieren
Führen Sie im Ordner `windows\` folgendes Skript aus:
```cmd
build.bat
```
Das Skript kompiliert alle Java-Dateien mit `javac` und erzeugt die ausführbare `dist\KR-Compiler-IDE.jar`.

---

## 🏗️ Verzeichnisstruktur

```
windows/
├── src/
│   ├── kr/compile/
│   │   ├── KrTokenType.java        # KR Lexer Token-Typen
│   │   ├── KrToken.java            # Token-Definition
│   │   ├── KrLexer.java            # Lexikalischer Scanner
│   │   ├── KrParser.java           # AST-Grammatik-Parser
│   │   ├── KrType.java             # Typsystem
│   │   ├── KrSegment.java          # Sprach- und Modulsegmentierung
│   │   ├── KrFunction.java         # Funktions-Definitionen
│   │   ├── KrLanguage.java         # Mehrsprachigkeits-Unterstützung
│   │   ├── KrSymbolTable.java      # Symboltabelle & Scope-Verwaltung
│   │   ├── KrDiagnostic.java       # Syntax- und Typfehler
│   │   ├── KrCodeGenerator.java    # Multi-Target Code-Generator (C++20, Rust, Go)
│   │   ├── KrPipeline.java         # Multi-File Modul-Pipeline
│   │   ├── KrProjectConfig.java    # TOML-Parser für *.krproject
│   │   ├── DiagnosticParser.java   # Compiler-Fehler-Parser
│   │   ├── DocumentModel.java      # Dokumenten-Modell
│   │   ├── DocumentManager.java    # Tab- & Dateiverwaltung
│   │   ├── ProjectManager.java     # Projektverwaltung & Vorlagen
│   │   ├── ArtifactsManager.java   # Artefakt-Scanner & Starter
│   │   ├── CompilerEngine.java     # Nativer Desktop-Compiler (Clang, Rust, Go, CMake)
│   │   ├── BuildManager.java       # Asynchroner Build-Manager
│   │   ├── VcpkgManager.java       # vcpkg Paket-Verwaltung
│   │   ├── KrAgentCore.java        # Autonomer KI-Programmieragent (ReAct)
│   │   └── windows/
│   │       ├── KrWindowsIde.java         # Haupt-Anwendungsfenster (GUI)
│   │       ├── CodeEditorPanel.java      # Multi-Tab Quellcode-Editor
│   │       ├── ProjectExplorerPanel.java # Projekt-Dateibaum mit Kontextmenü
│   │       ├── BuildConsolePanel.java    # Build-Konsole & Plattform-Wahl
│   │       ├── ArtifactsPanel.java       # Artefakte-Tabelle mit Ausführen-Button
│   │       ├── AgentDialog.java          # KI-Agenten-Dialog mit Export & Kopieren
│   │       └── SyntaxHighlighting.java   # Syntax-Hervorhebung
│   └── org/json/                   # Autarkes, abhängigkeitsfreies JSON-Paket
├── bin/                            # Kompilierte .class-Dateien
├── dist/
│   └── KR-Compiler-IDE.jar         # Fertig gepacktes, ausführbares Programm
├── Start-KR-IDE.bat                # Windows-Starter
├── Start-KR-IDE-Console.bat        # Windows-Starter mit Konsole
├── build.bat                       # Re-Build-Skript
├── Manifest.txt                    # JAR-Manifest
└── README.md                       # Diese Dokumentation
```
