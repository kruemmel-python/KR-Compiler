# Aktuelle Release-Hinweise

## KR Compiler IDE v4.2.1

### Automatisches Adaptive Compute

- Kanonische FP32-Schleifen für Vektoraddition und Vektormultiplikation werden
  aus normalem `.kr`-Quellcode automatisch in die vorhandene portable
  Adaptive-Compute-ABI übersetzt.
- Die Erkennung verwendet Lexer-Tokens, Analyzer-Entscheidungen und exakte
  Quellpositionen. Es findet keine Regex-Quelltextersetzung statt.
- Unsichere, nicht unterstützte oder nicht eindeutig typisierte Schleifen
  bleiben unverändert.
- `AUTO`, `CPU_ONLY`, `GPU_ONLY` und `VERIFY` behalten ihre festgelegte
  Laufzeitsemantik. Ein fehlgeschlagener notwendiger GPU-Aufruf wird sichtbar
  beendet und fällt nicht unbemerkt auf CPU zurück.
- Windows, Linux und Android verwenden automatisch die CC_OpenCL-Runtime aus
  der portablen IDE; Benutzerprojekte müssen sie nicht kopieren.

### Beispiel und Verifikation

- `KR_AUTO_COMPUTE_TEST` enthält ausschließlich normale `.kr`-Schleifen und
  keine manuellen Compute- oder OpenCL-Aufrufe.
- Windows- und Linux-Programme wurden erzeugt und gestartet.
- Der Android-ARM64-Pfad wurde einschließlich CC_OpenCL erfolgreich gelinkt.
- Addition und Multiplikation wurden auf einer AMD-`gfx90c`-GPU ausgeführt;
  die Telemetrie meldete für beide Operationen `backend: gpu`.

Download und Prüfsummen:
[GitHub-Release v4.2.1](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.2.1)
