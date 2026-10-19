# KR_16_PARALLEL_GPU_STRESS

Dieses KR-Projekt untersucht die gleichzeitige Nutzung der vorhandenen
Adaptive-Compute-Runtime durch 16 C++20-Worker. Die beiden Rechenschleifen sind
normaler `.kr`-Quellcode. Es gibt keine manuellen `kr_compute_add_f32`- oder
`kr_compute_mul_f32`-Aufrufe und keinen OpenCL-Code im Projekt.

## Testumfang

- 16 Worker mit gemeinsamem Startsignal
- 20 Iterationen pro Worker
- je Iteration eine FP32-Vektoraddition und eine FP32-Vektormultiplikation
- 8192 Elemente pro Operation
- exakt 640 Compute-Operationen
- getrennte Ein-, Summen- und Produktpuffer pro Worker
- deterministische Eingangsdaten
- vollständiger CPU-Referenzvergleich aller Ergebnisse
- sichtbare Fortschritts- und Abschlussausgabe auf Windows und Android
- JSONL-Telemetrie mit Worker-ID, Iteration, Operation, Backend, Gerät,
  Fehlercode, Kernelzeit und Gesamtzeit

## Bauen und starten

Den Ordner `examples/KR_16_PARALLEL_GPU_STRESS` in der KR Compiler IDE öffnen,
als Ziel **Windows x64** oder **Android ARM64** wählen und **Kompilieren**
drücken. Unter Windows das erzeugte Programm in `KR_BUILD` starten. Auf Android
die erzeugte APK installieren und öffnen; der Konsolen-Runner zeigt den
Fortschritt und das Endergebnis in einer scrollbaren Ansicht.

Die Android-App exportiert `adaptive_compute_telemetry.jsonl` nach Abschluss
über den vorhandenen Runner in den Download-Ordner.

## PASS-Kriterien

`PASS` erscheint nur, wenn alle folgenden Bedingungen erfüllt sind:

- 16 Worker wurden gestartet und sauber beendet;
- genau 640 Operationen wurden erfasst;
- alle 640 Operationen melden `backend: gpu`;
- kein CPU-Fallback trat auf;
- kein Runtime-Fehler trat auf;
- die vollständige numerische Prüfung meldet null Fehler.

Ein erfolgreicher Build allein ist kein GPU-Nachweis. Bei fehlendem OpenCL-
Gerät oder einem CPU-Fallback beendet sich das Programm mit `FAIL`. Die
einzelnen Entscheidungen bleiben in der Telemetriedatei nachvollziehbar.

## Automatische Transformation prüfen

Nach dem Build muss `adaptive_compute_plan.json` die Additions- und
Multiplikationsschleife als `GPU_ELIGIBLE` enthalten. Im generierten C++ müssen
Aufrufe von `kr_compute_add_f32` und `kr_compute_mul_f32` stehen, obwohl diese
Namen in `main.kr` nicht vorkommen.

## Nebenläufigkeitsmodell

Die Runtime hält Operationsstatus und letzte Telemetrie pro Thread. Das
Anhängen einer JSONL-Zeile wird kurz synchronisiert. Da CC_OpenCL wiederverwendete
Kernelobjekte besitzt, ist nur das Setzen ihrer Argumente zusammen mit dem
Enqueue geschützt; Datentransfer, Warten, CPU-Referenzprüfung und die übrige
Worker-Ausführung liegen nicht unter einer globalen Testsperre.

CC_OpenCL verwendet aktuell eine gemeinsame Geräte-Command-Queue. Der Test
belastet damit 16 gleichzeitig aufrufende Host-Threads und erkennt
Threadsicherheits- sowie Zuordnungsfehler. Er behauptet nicht, dass der Treiber
16 unabhängige Hardware-Queues bereitstellt.

## Nachgewiesener Stand

Am 19. Oktober 2026 wurden jeweils drei vollständige Läufe erfolgreich auf
Windows (AMD `gfx90c`) und auf einem physischen Android-Gerät (Qualcomm Adreno
732) ausgeführt. Jeder Lauf enthielt exakt 640 GPU-Operationen, null
CPU-Fallbacks, null Runtime-Fehler und null numerische Fehler. Details,
Artefakt-Prüfsummen und die getrennte Fehler-/Korrekturliste stehen in
[`TEST_RESULTS.md`](TEST_RESULTS.md); die Rohzusammenfassungen liegen unter
[`logs`](logs/).
