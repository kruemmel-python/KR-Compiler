# Testprotokoll – 19. Oktober 2026

## Nachgewiesene Erfolge

- Der KR-Compiler erkennt die beiden normalen Schleifen in `main.kr` als
  `ELEMENTWISE_ADD` und `ELEMENTWISE_MULTIPLY` mit `GPU_ELIGIBLE`.
- Der erzeugte Code enthält `kr_compute_add_f32` und `kr_compute_mul_f32`;
  der Projektquelltext enthält keine manuellen Compute- oder OpenCL-Aufrufe.
- Windows x64 wurde erfolgreich zu `programm.exe` kompiliert.
- Android ARM64 wurde erfolgreich zu einer signierten Runner-APK kompiliert.
- Drei reale Windows-Läufe auf AMD `gfx90c` endeten mit `PASS`.
- Drei reale Android-Läufe auf einem RMX3853 mit Qualcomm Adreno 732 endeten
  sichtbar in der Runner-Oberfläche mit `PASS`.
- Jeder der sechs Läufe meldete 16/16 gestartete und beendete Worker,
  640/640 Compute-Operationen, 640 GPU-Backends, null CPU-Fallbacks, null
  Runtime-Fehler und null numerische Fehler.
- Jeder geprüfte Lauf erzeugte exakt 640 JSONL-Datensätze: je 320 Additionen
  und Multiplikationen, Worker 0–15, Iterationen 0–19, Fehlercode 0.

## Aufgedeckte Fehler

Die generierte Adaptive-Compute-Runtime besaß vor diesem Test gemeinsam
veränderliche Operations- und Telemetriezustände. Gleichzeitige Worker konnten
sich dadurch den letzten Telemetrieeintrag überschreiben. GPU-Initialisierung
und JSONL-Anhängen waren nicht einmalig beziehungsweise nicht synchronisiert.
Außerdem verwendet CC_OpenCL gemeinsame Kernelobjekte; paralleles Setzen ihrer
Argumente und Enqueue ohne Schutz ist nicht threadsicher.

## Vorgenommene Korrekturen

- Operationsstatus und letzter Telemetrieeintrag sind jetzt threadlokal.
- Worker-ID und Iteration werden über einen kleinen Trace-Kontext eindeutig
  jeder Operation zugeordnet.
- Compute-Modus und GPU-Index werden atomar gelesen und geschrieben.
- GPU-Initialisierung erfolgt einmalig über `std::call_once`.
- Nur der kritische Abschnitt aus Kernelargumenten und Enqueue ist gesperrt;
  Transfers, Warten, Validierung und Worker-Ausführung liegen nicht unter
  einer globalen Berechnungssperre.
- Das JSONL-Anhängen und Änderungen am Telemetriepfad sind kurz
  synchronisiert.
- Dieselbe Runtime-Erzeugung wurde in Android-, Windows- und Linux-IDE
  aktualisiert und durch die vorhandenen Java-Tests abgesichert.

CC_OpenCL nutzt weiterhin eine gemeinsame Geräte-Command-Queue. Bewiesen ist
damit die korrekte, threadsichere Nutzung durch 16 gleichzeitig gestartete
Host-Worker, nicht das Vorhandensein von 16 unabhängigen Hardware-Queues.

## Reale Läufe

| Plattform | Lauf | Zeit | GPU | Fallback | Runtimefehler | Zahlenfehler | Ergebnis |
|---|---:|---:|---:|---:|---:|---:|---|
| Windows / AMD gfx90c | 1 (Kaltstart) | 7073,89 ms | 640 | 0 | 0 | 0 | PASS |
| Windows / AMD gfx90c | 2 | 914,952 ms | 640 | 0 | 0 | 0 | PASS |
| Windows / AMD gfx90c | 3 | 890,186 ms | 640 | 0 | 0 | 0 | PASS |
| Android / Adreno 732 | 1 | 5255,27 ms | 640 | 0 | 0 | 0 | PASS |
| Android / Adreno 732 | 2 | 4408,91 ms | 640 | 0 | 0 | 0 | PASS |
| Android / Adreno 732 | 3 | 4153,79 ms | 640 | 0 | 0 | 0 | PASS |

## Buildartefakte der Prüfung

- Windows `programm.exe`: 3.927.944 Bytes, SHA-256
  `9F39269180CC75E24CAC3373327CB5920F2872397ADBE1F258FE59C7C7C7727F`
- Android `programm-android-arm64.apk`: 8.238.110 Bytes, SHA-256
  `C8CAE9D951B4B0534E492EC27B839241F3F011E9C445244AB39A023DB2A92EAB`
- Android-App-ID: `kr.compile.runner`; Aktivität:
  `kr.compile.runner.RunnerActivity`

## Nicht durchgeführte Hardwareprüfungen

Andere Android-GPUs (insbesondere Mali), weitere Windows-GPUs und andere
Treiberstände waren nicht Teil dieses Auftrags. Für AMD `gfx90c` unter Windows
und Qualcomm Adreno 732 auf physischer Android-Hardware wurde die Ausführung
tatsächlich durchgeführt; diese beiden Prüfungen sind nicht nur Buildtests.
