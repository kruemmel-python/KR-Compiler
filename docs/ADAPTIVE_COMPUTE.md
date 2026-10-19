# Adaptive Compute

## Compiler-Pipeline

Die Integration liegt zwischen Analyse und C++-Codegenerierung:

1. `KrParser` erzeugt sprachspezifische `KrSegment`-Objekte.
2. `KrAdaptiveComputeAnalyzer` bewertet Schleifen und erstellt den
   `KrComputePlan`.
3. `KrAdaptiveComputeLowerer` verwendet Lexer-Tokens und Quell-Offsets, um nur
   freigegebene Schleifen zu ersetzen.
4. `KrCodeGenerator` erzeugt die nativen Translation Units.
5. `KrAdaptiveComputeCodeGenerator` ergänzt Dispatcher, CPU-Referenz,
   CC_OpenCL-Pfad und Telemetrie.

## Automatisch transformierte Form

Aktuell werden kanonische FP32-Schleifen für Addition und Multiplikation
transformiert:

```cpp
for (int i = 0; i < count; i++) {
    output[i] = left[i] + right[i];
}
```

Die Freigabe verlangt unter anderem:

- Startindex `0`, obere exklusive Grenze und Schrittweite `1`
- genau eine elementweise Ausgabezuweisung
- gleicher Induktionsindex für Ausgabe und beide Eingaben
- nachweisbare FP32-Pufferdeklarationen
- keine unbekannten Aufrufe, Kontrollflüsse oder schleifengetragenen
  Abhängigkeiten
- sichere, nebenwirkungsfreie Schleifengrenze

Kann einer dieser Punkte nicht bewiesen werden, bleibt der Originalcode
unverändert. Weitere Runtime-Operationen wie Matrixmultiplikation, Reduktion,
Tensoren, Bildskalierung und Conv2D sind verfügbar, werden aber noch nicht in
allen Quellformen automatisch abgesenkt.

## Modi und Fehlerverhalten

| Modus | Verhalten |
|---|---|
| `AUTO` | Dispatcher wählt CPU oder GPU; CPU-Fallback ist erlaubt. |
| `CPU_ONLY` | Die portable CPU-Referenz wird verwendet. |
| `GPU_ONLY` | Vorgesehene GPU-Ausführung ist verpflichtend; Fehler beendet den Aufruf. |
| `VERIFY` | GPU-Ausgabe wird gegen die CPU-Referenz validiert. |

## Telemetrie

Die JSONL-Telemetrie enthält Operation, Backend, Entscheidung, Gerät,
Runtime-Version, Initialisierungs-, Transfer- und Kernelzeiten, Byte-Mengen,
Spitzenspeicher, Validierungsstatus und Fehlercode. Ohne reale Energiequelle
werden keine Verbrauchswerte geschätzt.

## Beispiel

[`KR_AUTO_COMPUTE_TEST`](../KR_AUTO_COMPUTE_TEST/) ist das verbindliche
Ende-zu-Ende-Beispiel. Sein `main.kr` enthält keine `kr_compute_*`- und keine
OpenCL-Aufrufe.
