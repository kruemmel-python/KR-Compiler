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
    - Erhöhte Timeouts (60s Verbindungsaufbau, 300s Modell-Inferenz/Read).
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
- **Hochkontrast-Dark-Theme**: Einheitliches, augenschonendes Dark-Design im VS-Code-Stil (#1E1E1E / #252526 / #007ACC) über alle Panels, Menüleisten, Explorer, Buttons, TabbedPanes und Agenten-Dialoge hinweg.
- Startbar über `windows/Start-KR-IDE.bat`, `windows/Start-KR-IDE-Console.bat` oder direkt via `KR-Compiler-IDE.jar`.

---

## Prüfsummen & Assets

- **Android Release**: `KR-Compiler-Android-IDE-v4.0.3.apk`
  - **Größe**: `744.024.048 Bytes`
  - **SHA-256**: `ac7823db51e65b920d1445ea5ab6db8883d2fd692f4110412f1c61a1be35094d`
  - **Signatur**: Release-Entwicklungsschlüssel (v4.0-kompatibel)

- **Windows Desktop Edition**: `KR-Compiler-IDE.jar`
  - **Größe**: `153.318 Bytes`
  - **Plattform**: Windows 10 / 11 (Java 17+)
  - **Starter**: `windows/Start-KR-IDE.bat`
