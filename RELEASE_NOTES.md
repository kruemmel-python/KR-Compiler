# Aktuelle Release-Hinweise

## KR Compiler IDE v4.3.1

### Android-SDL3-Hotfix

- `target_type = "gui"` aus einer `.krproject`-Datei wird jetzt bis zum
  Android-Compiler- und Packaging-Schritt weitergegeben.
- Der direkte modulare KR-C++-Pfad bindet die SDL3-Header aus dem portablen
  Offline-Bestand `toolchain/sources/sdl3/include` ein.
- SDL3 wird vor dem Linken als Android-ARM64-Archiv gebaut und zusammen mit
  dem GUI-Runner paketiert.
- Zwei Regressionstests sichern GUI-Konfiguration und eingebetteten
  SDL3-Headerpfad ab.
- Unnötige Clang-Warnungen des gemischten C/C++-Compilerpfads wurden entfernt.

### SDL3 auf allen drei Zielsystemen

- SDL 3.4.18 ist Bestandteil der portablen Windows-, Android- und
  Linux-Auslieferung; Entwickler müssen SDL, ein SDK oder Entwicklungspakete
  nicht nachinstallieren.
- Die Android-IDE enthält die verifizierten SDL3-Quellen und baut sie offline
  für Android ARM64, Windows x64 oder Linux x64 passend zum gewählten Ziel.
- Die Windows-IDE liefert Header, Import-/statische Bibliothek und `SDL3.dll`.
- Die Linux-IDE liefert Header, Linkbibliothek und `libSDL3.so.0`; die
  erzeugte Laufzeitdatei wird neben das KR-Programm kopiert.
- Das unveränderte modulare Beispiel `MDraft_KR_CrossPlatform` wurde für alle
  drei Ziele gebaut. Windows und Linux wurden außerdem als GUI-Prozess
  gestartet; die Android-APK wurde erstellt und strukturell geprüft.

### Ein Projekt für drei Systeme

- Eine `.krproject`-Datei und ein gemeinsamer Quellbaum reichen für Android,
  Windows und Linux aus. Es gibt keine getrennten Plattformprojekte.
- `packages = ["sdl3"]` aktiviert SDL3, und die IDE übernimmt Header,
  Verlinkung, Runner sowie das Kopieren der Laufzeitdateien.

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
[GitHub-Release v4.3.1](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.3.1)
