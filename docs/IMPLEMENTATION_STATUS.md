# Implementierungsstatus

Stand: v4.2.1

## Implementiert und geprüft

- eigenständige portable IDEs für Android ARM64, Windows x64 und Linux x64
- `.kr`-Frontend mit Lexer, Parser, Symboltabelle, Codegenerator und Source Map
- native C++20-, Rust- und Go-Buildpfade
- portable Adaptive-Compute-ABI mit CPU-Referenzen und CC_OpenCL-GPU-Pfaden
- Modi `AUTO`, `CPU_ONLY`, `GPU_ONLY` und `VERIFY`
- automatische tokenbasierte Absenkung kanonischer FP32-Vektoraddition und
  -multiplikation
- JSONL-Telemetrie für Gerät, Entscheidung, Zeiten, Transfers, Speicher,
  Validierung und Fehler
- Windows-GPU-Test auf AMD `gfx90c` für automatisch transformierte Addition
  und Multiplikation
- Linux-x64-Programm kompiliert und unter Ubuntu/WSL2 gestartet
- Android-ARM64-Ausgabe einschließlich CC_OpenCL erfolgreich gelinkt
- Android und Ubuntu vom Projektinhaber praktisch getestet und als
  funktionsfähig bestätigt
- signierte Android-v4.2.1-APK, verifizierte Windows- und Linux-Archive

## Bekannte Grenzen

- Matrixmultiplikation, Reduktionen, allgemeine Tensorformen, Bildverarbeitung
  und Conv2D besitzen Runtime-Unterstützung, aber noch kein vollständiges
  automatisches Quellschleifen-Lowering für alle Formen.
- Telemetrie ist operationsbezogen und dateibasiert; ein permanentes
  Live-Dashboard ist noch nicht vollständig.
- Energieverbrauch wird ohne reale Messquelle nicht geschätzt.

Android und Ubuntu gelten im aktuellen Projektstand als funktionsfähig. Wenn
die Compute-Sicherheit oder GPU-Gerätefähigkeit zur Laufzeit nicht nachweisbar
ist, bleibt im Modus `AUTO` der CPU-Pfad die verbindliche Ausführung.
