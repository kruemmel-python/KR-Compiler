# Adaptive GPU Runtime – Stand 2026-10-18

## Implementiert

- Die Geräteerkennung fragt FP64 und atomare Erweiterungen zur Laufzeit ab, statt Herstellerlisten zu verwenden.
- Die Kernel-Basis bleibt OpenCL C 1.2 und unterstützt Geräte ohne OpenCL-2.0-Symbole.
- Strict-Kernel werden vor Fast-Math-Kerneln gebaut. Scheitert Fast-Math, bleibt die Strict-Variante erhalten.
- Nicht essentielle Kernel-Build-Fehler deaktivieren nur den betroffenen Kernel. Die OpenCL-Queue und der Kontext bleiben aktiv.
- Der essentielle FP32-Kernel `add_elementwise` muss erfolgreich erstellt werden, sonst wird die GPU-Initialisierung als Fehler gemeldet.
- Optionale Subsysteme (Brain, Mycel, SQSE, SubQG) dürfen den Basis-GPU-Pfad nicht vollständig deaktivieren.

## Sicherheits- und Funktionsgrenzen

- Nicht verfügbare Kernel-Handles dürfen niemals gestartet werden. Aufrufer müssen Fehlerwerte prüfen.
- Die Kompilierung beweist noch keine erfolgreiche Ausführung. Der Android-GPU-Laufzeittest muss `execute_add_on_gpu` samt Ergebnisvergleich erfolgreich durchlaufen.
- Es ist keine Vulkan-Implementierung und keine vollständige automatische CPU-Fallback-Engine enthalten.
- Für FP64-Anforderungen ist keine stillschweigende Reduktion auf FP32 zulässig.
- Dynamische Laufzeitfehler, Device-Loss, Memory-Tiling und automatische Workgroup-Verkleinerung sind nicht vollständig umgesetzt.
- Die Tests wurden unter Linux x64 kompiliert; Android Mali/Adreno sind auf echter Hardware zu validieren.

## Automatic execution log export

The `test_cc_opencl` runtime test now writes a complete `cc_opencl_execution.log` automatically while continuing to print to the terminal. On Android/Linux the log captures C driver output (`printf` / `fprintf(stderr, ...)`) as well as C++ test output, including OpenCL build errors and numerical test results. This is a runtime log, not the CMake build log.

The output path is chosen in order: `CC_OPENCL_LOG_PATH` (full writable filename), `CC_OPENCL_LOG_DIR` (directory), current working directory, then `$TMPDIR`. The absolute or relative path is printed as `[LOG] Saved execution log: ...`. Android app sandbox restrictions can prevent direct access to Downloads; the host KR IDE should expose/copy the resulting file via its document picker or configure a writable export path. The library does not bypass Android scoped storage.

Example: `CC_OPENCL_LOG_PATH=/writable/path/gpu_run.log ./test_cc_opencl`. Logs are overwritten each run. For crash-proof streaming, each chunk is written without userspace file buffering, but abrupt process termination may still truncate the file.
