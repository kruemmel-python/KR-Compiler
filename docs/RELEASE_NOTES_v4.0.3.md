# KR Compiler Android IDE v4.0.3 – Release Notes

Version 4.0.3 erweitert die KR Compiler Android IDE um zwei bahnbrechende Kernfeatures:
1. **Vollständiger Support für modulare Großprojekte (`*.krproject`)** und die Multi-File KR-Architektur (C++20, Rust, Go) für Enterprise-, Forschungs- und Simulationssysteme (wie `KR_FORGE`) für alle drei Zielplattformen (**Android ARM64**, **Windows x64**, **Linux x64**).
2. **Integrierter autonomer KI-Programmieragent („AGENT“)** direkt in der mobilen IDE mit Werkzeugschleife (`list`, `read`, `write`, `build`, `done`), OpenAI-kompatibler Modellanbindung und strikter Sandbox-Sicherheit.

Zudem wurden die Versionsanzeige und der Infobereich vereinheitlicht und modernisiert.

---

## Neuerungen in Version 4.0.3

### 1. Neuer autonomer KI-Programmierassistent („AGENT“)
- **Nahtlose Integration in die IDE**: Neuer Menü- und Navigationsbutton **`AGENT`** in der Hauptleiste (`CODE | DATEIEN | BUILD | ARTEFAKTE | AGENT`).
- **Autonome Problemlösungsschleife (ReAct)**:
  - Führt auf Nutzerauftrag mehrstufige Entwicklungsaufgaben eigenständig aus.
  - Erkennt Projektstrukturen (`list`), liest Quelldateien (`read`), modifiziert oder erstellt Code (`write`) und stößt den echten IDE-Kompilierungslauf an (`build`).
  - Analysiert Compiler-Fehlermeldungen aus dem `BuildManager` und korrigiert Syntax-, Typ- und Linker-Fehler iterativ.
- **Offener Modell-Standard**:
  - Unterstützt jeden OpenAI-kompatiblen HTTPS-Endpunkt (OpenAI GPT-4o, Anthropic via Proxy, Mistral, Groq, OpenRouter).
  - Ermöglicht den Betrieb privater, lokaler Open-Source-Modelle über **Ollama** oder LM Studio via `http://127.0.0.1:...` oder im lokalen Netzwerk.
- **Sicherheits- & Freigabesystem („Human-in-the-Loop“)**:
  - **Freigabepflicht**: Dateiänderungen und Build-Starts erfordern die ausdrückliche Genehmigung durch den Entwickler mit detaillierter Vorschau.
  - **Strikte Projekt-Sandbox (`safeFile`)**: Zugriff ist strikt auf das geöffnete Projektverzeichnis beschränkt. Systempfade, fremde Ordner, `.git`, `.gradle` und `.kragent` sind physisch gesperrt.
  - **Unantastbarer Compilerkern**: Der interne Compiler (`CompilerEngine`, `KrPipeline`, Toolchains, Sysroots) kann vom Agenten weder eingesehen noch verändert werden.
  - **Revisionssicheres Audit-Log**: Sämtliche Aktionen werden lückenlos mit Zeitstempeln im Journal `kr_agent_audit.jsonl` festgehalten.
  - **Volle Unterstützung für SAF- und Dateimanager-Projekte**: Der Agent analysiert und editiert nun sowohl frisch erstellte als auch über den Android-Dateiexplorer geöffnete externe Projektordner (Storage Access Framework).
  - **Persistenter Logverlauf & Live-Feedback**: Der Agentenfortschritt wird kontinuierlich angezeigt (ProgressBar, Status-Banner, AGENT-Button-Indikator). Beim Schließen des Dialogs läuft der Agent im Hintergrund weiter, ohne dass das Logfenster zurückgesetzt wird.
  - **Zwischenablage & Projekt-Export**:
    - **`📋 Kopieren`**: Überträgt das gesamte Agentenprotokoll mit einem Klick in die Android-Zwischenablage.
    - **`💾 Export`**: Speichert das Protokoll formatiert mit Projektname und Zeitstempel als `kr_agent_log.txt` direkt in den Projektordner und aktualisiert die IDE-Dateiansicht.
  - **Robuste Netzwerk- & Modell-Konnektivität**:
    - Erhöhte Timeouts (60s Verbindungsaufbau, 600s / 10 Minuten Modell-Inferenz/Read für LM Studio & Ollama).
    - Automatische Kontext-Kompaktierung (`compactHistory`), um lokale Modelle bei längeren Unterhaltungen performant zu halten.
    - `usesCleartextTraffic="true"` und LAN-IP-Freigabe (`192.168.x.x`, `10.x.x.x`, `localhost`) für nahtlose Anbindung lokaler LLM-Server (Ollama, LM Studio).
    - Detaillierte Fehlerdiagnostik bei Verbindungsproblemen mit konkreten Lösungshinweisen.
  - **Sofortiger Abbruch**: Laufende Agentenprozesse und Builds können jederzeit über **`STOPP`** beendet werden.

---

### 2. Modulare Multi-File KR-Architektur (`*.krproject`)
- **Deklarative Projektkonfiguration**: Neue TOML-basierte Konfigurationsdatei `*.krproject` mit Sektionen für Metadaten (`[project]`), rekursive Scan-Ordner (`[modules]`), Drittpakete (`[dependencies]`) und Compiler-Flags (`[build]`).
- **Hierarchische Verzeichnisstrukturen**: Volle Unterstützung für Enterprise-Strukturen am Referenzmodell `KR_FORGE` (`app/`, `gui/`, `research/`, `compute/`, `orchestration/`, `storage/`, etc.).
- **Automatischer Modul-Scan & Pfadnormalisierung**: `KrPipeline` erkennt alle deklarierten Unterordner, relativiert Quellpfade gegen das Projektverzeichnis und verhindert Namenskollisionen.
- **Inkrementelle Translation Units**: `KrCodeGenerator` zerlegt modulare `.kr`-Dateien in getrennte C++ Translation Units unter `modules/<modul_name>_kr.cpp`.
- **Konsolidierte Rust- und Go-Module**: Modulübergreifende Konsolidierung von Rust-Funktionen in `kr_rust/src/lib.rs` und Go-Routinen in `kr_go/main.go`.
- **Zentraler ABI-Header `kr_bridge.h`**: Automatische Prototypen-Generierung für alle Funktionen über Modul- und Sprachgrenzen hinweg.

---

### 3. Multi-Dependency Management mit vcpkg
- Automatische Erzeugung von kanonischen `vcpkg.json`-Manifesten direkt aus der `[dependencies]`-Sektion von `*.krproject`.
- Nahtlose Verknüpfung externer C/C++-Bibliotheken (z. B. `opencl`, `sqlite3`, `nlohmann-json`) mit der integrierten Toolchain auf allen drei Zielsystemen.

---

### 4. UI- & Versions-Harmonisierung
- **Menüeintrag vereinfacht**: Der Menüpunkt lautet nun prägnant **„Über KR IDE“** (ohne statisch veraltende Versionsanhängsel).
- **Entwickler-Würdigung**: Im Infodialog wird **Entwickler: Ralf Krümmel** prominent aufgeführt.
- **Dynamische Versionsauflösung**: `MainActivity` ermittelt die aktive Versionsnummer (`v4.0.3`) nun dynamisch via `PackageManager` / `getAppVersionName()`.

---

### 5. Physische Verifikation auf Testgeräten
- Getestet auf **Samsung Galaxy Tab S6 Lite (SM-P615)** und **Realme GT 6T (RMX3853)**:
  - **Android ARM64**: `programm-android-arm64.apk` (2.8 MB) + `libuser.so` (2.3 MB) – **PASS**
  - **Windows x64**: `programm.exe` (8.5 MB) – **PASS**
  - **Linux x64**: `programm` (7.9 MB) – **PASS**
- Instrumentation Test Runner: `result=3 Sprach-Builds erfolgreich` (Exit Code `-1`).

---

### 6. Volle Abwärtskompatibilität
- Alle 18 realen C/C++ GitHub-Repositories (54/54 Builds) bleiben voll funktionsfähig.
- Single-File `.kr`-Projekte, native Rust- und Go-Projekte laufen unverändert weiter.

---

### 7. Neue Windows Desktop Edition (1 zu 1 Portierung)
- Vollständiger 1:1 Desktop-Port im neuen Projektordner `windows/`.
- Nativ lauffähig auf Windows 10 / 11 (64-Bit) mit Java 17+.
- Enthält die gesamte KR Pipeline, Projekt-Explorer, Multi-Tab Editor mit Syntax-Highlighting, Build-Konsole, Artefakte-Verwaltung mit 1-Klick-Ausführung (`.exe`) und den autonomen KI-Agenten (`AGENT`).
- **Vollständige KR-Projekt-Parität**: Sämtliche KR-Projekte, die in der Android-App erstellt werden können (Modulare `*.krproject`, Single-File `.kr`, C++20, Rust, Go), lassen sich 1:1 in der Windows-Version anlegen, bearbeiten und fehlerfrei kompilieren.
- **Zero-Setup Autonomie & ABI-Fallback-Bridge**: Wie bei der Android-APK sind keine externen SDKs auf dem PC erforderlich. Rust-Module werden bei vorhandenem Cargo direkt gebaut; für externe Komponenten (z. B. Go) generiert die IDE automatisch typgerechte C++ ABI-Bridges, sodass das Projekt auf jedem Windows-PC ohne manuelle SDK-Installationen kompiliert.
- **Saubere modulare Übersetzung & Subsystem-Linker**: Trennung von monolithischer und modularer Übersetzungseinheiten verhindert `multiple definition`-Kollisionen. Korrekte Subsystem-Steuerung (`-mconsole`, `-Wl,--allow-multiple-definition`) verhindert `WinMain`-Fehler.
- **Multi-Target-Erfolgsnachweis**: Alle drei Zielplattformen (**Windows x64 `.exe`**, **Linux x64**, **Android ARM64 `libuser.so`**) bauen erfolgreich durch.
- **Hochkontrast-Dark-Theme**: Einheitliches, augenschonendes Dark-Design im VS-Code-Stil (#1E1E1E / #252526 / #007ACC) über alle Panels, Menüleisten, Explorer, Buttons, TabbedPanes und Agenten-Dialoge hinweg.
- **Saubere Glyphen- & Tag-Darstellung**: Professionelle Compiler-Log-Tags (`[INFO]`, `[EXEC]`, `[ERFOLG]`, `[FEHLER]`) und native Unicode-Symbole (`▶`, `■`, `✕`, `↺`, `↗`).
- **Toolchain-Umgebungs-Injektion**: Automatische Einbindung der MinGW/MSYS2/LLVM-Binärverzeichnisse in den Subprozess-`PATH`, wodurch stumme DLL-Ladefehler von `g++.exe` (`cc1plus.exe`, `as.exe`, `ld.exe`) behoben sind.
- Startbar über `windows/Start-KR-IDE.bat`, `windows/Start-KR-IDE-Console.bat` oder direkt via `KR-Compiler-IDE.jar`.

---

### 8. Intelligente KrSourceMap & Autonome KI-Fehlerbehebung (LM Studio & Ollama)
- **Bidirektionale KrSourceMap**:
  - Exaktes Mapping von generierten Zwischen- und Build-Dateien (`KR_BUILD/rust_module/src/lib.rs`, `KR_BUILD/go_module/main.go`, `KR_BUILD/modules/*.cpp`) zurück auf die originären `.kr`-Quelldateien, Funktionsdeklarationen und Zeilennummern.
  - Automatische Persistierung als `KR_BUILD/source_map.json` bei jedem Pipeline- und Codegenerator-Lauf.
- **Transparente Pfad-Redirection**:
  - Wenn ein lokales KI-Modell (z. B. `gemma-4-e2b`) aus Compiler-Logs abgeleitete Pfade wie `src/lib.rs`, `lib.rs` oder `KR_BUILD/...` per `read` anfordert, fängt `KrAgentCore` dies transparent ab und liefert direkt den Inhalt der zuständigen `.kr`-Quelldatei (z. B. `security/policy.kr`) mit Zeilennummern und Hinweis-Präfix.
  - Führt das Modell ein `replace` auf einem generierten Pfad aus, wird die Änderung automatisch in der zugrundeliegenden `.kr`-Originaldatei gespeichert.
- **Smarte Diagnose-Extraktion (`extractDiagnosticHints`)**:
  - Vollständiges Parsing von Cargo/Rust- (`--> src\lib.rs:...`) und Go-Compiler-Meldungen.
  - Automatische Identifikation der fehlerhaften Zeile in der `.kr`-Datei und Bereitstellung einer sofort ausführbaren `replace`-Befehlsvorlage (inkl. automatischer Ergänzung fehlender schließender Klammern bei Delimiter-Fehlern).
- **Robuste Fehlererkennung**:
  - Cargo- und Go-Kompilierfehler werden bei lokal vorhandenen Toolchains nicht mehr als Warnung verschluckt, sondern führen zu einem sauberen Build-Abbruch, der die KI-Fehlerdiagnose triggert.
- **LM Studio Stabilitäts-Upgrade**:
  - Modell-Antwort-Timeout auf 10 Minuten (`600.000 ms`) angehoben.
  - Kontext-Kompaktierung (`compactHistory`) zur Entlastung des KV-Caches lokaler Inferenz-Engines.

---

### 9. Härtung der Agenten-Pfadauflösung & Schleifenterminierung
- **Präzise Verzeichnis-Klassifizierung**:
  - Ordnerpfade (`gui`, `engine`, `storage`, `services`, `tests` etc.) werden bei Aufruf von `read` sofort als Verzeichnis erkannt und listen ihren Inhalt auf, statt fehlerhaft als Build-Artefakt eingestuft und auf `main.kr` umgeleitet zu werden.
- **Projektweite Dateisuche (`findFileInProject`)**:
  - Bei Aufruf von Quellcodedateien ohne Verzeichnisangabe (z. B. `read -> lifecycle.kr`) durchsucht der Agent rekursiv den Projektbaum und findet z. B. `app/lifecycle.kr` direkt als reguläre Quelldatei ohne Verfälschung oder Artefakt-Warnung.
- **Sichere Artefakt-Guards (`isGeneratedArtifactPath`)**:
  - Quellcode-Umleitungen werden ausschließlich für reale generierte Dateien (`src/lib.rs`, `main.go`, `KR_BUILD/...` oder per `KrSourceMap` registriert) aktiviert.
  - Für unbekannte oder ungültige Pfade gibt `findSourceForGeneratedFile` sauber `null` zurück – der pauschale Fallback auf `main.kr` wurde vollständig entfernt.
- **Post-Build Schleifenterminierung**:
  - Sobald ein Build mit `BUILD OK` erfolgreich durchläuft und die Zieldatei (z. B. `programm.exe`) erzeugt wurde, weist das System den Agenten explizit an, die Aufgabe unmittelbar mit `done` zu beenden. Unnötige Folgeaufrufe (wiederholtes Lesen, Verzeichnislisten oder Neuschreiben) lokaler LLMs werden unterbunden.
- **Idempotenz-Schutz beim Schreiben (`write`)**:
  - Übergibt ein Modell identischen Quellcode für eine bereits unveränderte Datei, wird der Schreibvorgang ohne Freigabedialog übersprungen und das Modell zur Beendigung angeleitet.

---

## Prüfsummen & Assets

- **Android Release**: `KR-Compiler-Android-IDE-v4.0.3.apk`
  - **Größe**: `744.024.048 Bytes`
  - **SHA-256**: `ac7823db51e65b920d1445ea5ab6db8883d2fd692f4110412f1c61a1be35094d`
  - **Signatur**: Release-Entwicklungsschlüssel (v4.0-kompatibel)

- **Windows Desktop Edition**: `KR-Compiler-IDE.jar`
  - **Größe**: `188.369 Bytes`
  - **SHA-256**: `059b9a8550f6813654b60b6b8a41519f362b8952f7248c3179bb46841dcb2c1b`
  - **Plattform**: Windows 10 / 11 (Java 17+)
  - **Starter**: `windows/Start-KR-IDE.bat`
