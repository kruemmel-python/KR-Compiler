# Aktuelle Release-Hinweise

## KR Compiler IDE v4.2.2

### Threadsicheres Adaptive Compute

- Operationsstatus und letzter Telemetrieeintrag sind pro Worker-Thread
  getrennt; parallele Aufrufe überschreiben sich nicht mehr gegenseitig.
- GPU-Initialisierung erfolgt einmalig und threadsicher.
- Das Anhängen der JSONL-Telemetrie ist synchronisiert und enthält zusätzlich
  Worker-ID und Iteration.
- Bei den von CC_OpenCL gemeinsam verwendeten Kernelobjekten wird nur das
  Setzen der Argumente samt Enqueue geschützt. Transfers, Warten, Validierung
  und Worker-Ausführung bleiben außerhalb dieser kurzen Sperre.
- Die Korrektur ist identisch in der Android-, Windows- und Linux-IDE
  enthalten. Die portablen Pakete benötigen weiterhin keine nachträglichen
  SDK-Downloads.

### Neuer 16-Thread-GPU-Stresstest

- `KR_16_PARALLEL_GPU_STRESS` startet 16 Worker gleichzeitig und führt pro
  Lauf exakt 640 automatisch transformierte FP32-Additionen und
  -Multiplikationen mit vollständiger CPU-Referenzprüfung aus.
- Drei reale Windows-Läufe auf AMD `gfx90c` und drei reale Android-Läufe auf
  Qualcomm Adreno 732 wurden erfolgreich ausgeführt.
- Alle sechs Läufe meldeten 640 GPU-Operationen, null CPU-Fallbacks, null
  Runtime-Fehler und null numerische Fehler.
- Das Beispielprojekt, seine Testprotokolle und ein separates ZIP gehören zum
  Release.

Download und Prüfsummen:
[GitHub-Release v4.2.2](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.2.2)
