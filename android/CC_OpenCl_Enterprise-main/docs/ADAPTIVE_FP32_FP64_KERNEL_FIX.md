# Adaptive FP32/FP64 Kernel Fix — 2026-10-18

## Ursache
Android Mali-G72 meldete `double precision constant requires cl_khr_fp64` in `embedding_lookup`, `izhikevich_neuron_step`, `lbm_collide_and_stream` und `subqg_simulation_step`.

## Umsetzung
In den vier betroffenen OpenCL-Quelltexten wird `CC_REAL(x)` durch `FP_TYPE_SIZE` aufgelöst: Bei 4-Byte-FP32 wird das Literal mit `f`-Suffix erzeugt; bei 8-Byte-FP64 bleibt das ursprüngliche Double-Literal erhalten. Dadurch bleibt die numerische FP64-Konstantenpräzision auf kompatiblen Windows-/Linux-GPUs erhalten. Die existierende geräteabhängige FP_TYPE-Auswahl bleibt unverändert.

Die Core-Kernel-Initialisierung zählt erfolgreiche und fehlgeschlagene Builds und meldet keine vollständige Verfügbarkeit mehr, wenn einzelne Kernel fehlschlagen. Optionale Kernel dürfen weiterhin ausfallen, ohne den GPU-Kontext zu zerstören.

## Validierung
Linux x64: CMake/Ninja Release Build von Shared Library und Testprogramm erfolgreich. Android ARM64, Mali-G72, Adreno, Windows x64 sowie echte FP64-GPU-Läufe sind in dieser Umgebung **nicht** getestet.

## Nutzung
Projekt in KR IDE importieren, Android ARM64 bauen, Testprogramm starten und `cc_opencl_execution.log` aus Downloads bereitstellen. Bei einem verbleibenden OpenCL-Compilerfehler das vollständige Build-Log beifügen.
