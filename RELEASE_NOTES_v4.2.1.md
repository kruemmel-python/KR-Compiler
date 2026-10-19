# KR Compiler IDE v4.2.1

## Automatic Adaptive Compute lowering

- Canonical FP32 vector-add and vector-multiply loops in ordinary `.kr` source
  are now lowered automatically to the existing portable Adaptive Compute ABI.
- Matching uses KR lexer tokens, exact source offsets, analyzer decisions and
  conservative FP32 declaration checks; no regular-expression source rewrite
  is used.
- Unsafe, unsupported or ambiguously typed loops remain unchanged on the
  original CPU path.
- `AUTO`, `CPU_ONLY`, `GPU_ONLY` and `VERIFY` retain their runtime semantics.
  Failed required GPU dispatches are surfaced and terminate the generated
  program instead of silently continuing on CPU.
- Windows, Linux and Android builds automatically bind the CC_OpenCL runtime
  already shipped with the portable KR IDE; projects no longer need to copy it.

## End-to-end example

`KR_AUTO_COMPUTE_TEST` contains a `.krproject` and `main.kr` with no manual
compute or OpenCL calls. Release validation produced and started Windows and
Linux executables, linked Android ARM64 output, and executed both transformed
operations on an AMD `gfx90c` GPU successfully.
