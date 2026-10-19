# KR Compiler IDE v4.2.0

Version 4.2.0 implements the portable Adaptive Compute runtime across Android,
Windows and Linux while preserving the offline, self-contained distributions.

## Native CPU/GPU runtime

- Every KR build generates `kr_adaptive_compute.h/.cpp` automatically.
- Portable operations: vector/tensor add and multiply, batched and unbatched
  matrix multiplication, tensor reduction, RGBA image scaling and Conv2D.
- Deterministic `AUTO`, `CPU_ONLY`, `GPU_ONLY` and `VERIFY` behavior.
- Checked dimensions and buffer sizes, scoped GPU cleanup, synchronous
  transfers, error propagation and replay-safe CPU fallback.
- VERIFY compares GPU results against the CPU reference with absolute/relative
  FP32 tolerances.

## Telemetry and agent

- JSONL telemetry includes device and runtime version, capabilities, decision,
  fallback, all timing phases, transfer bytes, peak GPU memory and validation.
- Android runner exports generated telemetry to `Download/`.
- The Android analysis dialog displays the latest available runtime record.
- The AI agent adds a read-only `adaptive` action and may not claim speed or
  energy improvements without measurements.

## Validation tooling

- Cold/warm benchmark summarizer and explicit energy status.
- Cross-platform regression runner records `PASS`, `FAIL` and `SKIPPED`.
- Physical-device Android discovery script records model/API/GPU facts without
  falsely claiming a GPU result test.
- The example project executes the generated dispatcher and writes telemetry.

## Verified in this release

- Clean Android unit-test and signed release build: 146 tasks successful.
- Windows x64 CPU reference test passed all six operation families.
- Physical AMD OpenCL (`gfx90c`) VERIFY test passed vector/tensor, matmul,
  reduction, RGBA image scaling and Conv2D comparisons.
- Windows and Ubuntu 24.04/WSL2 portable IDE sources compile successfully.
- Windows and Linux regression rows pass; absent Android hardware is `SKIPPED`.

## Remaining external validation

No Adreno/Mali phone or tablet was attached for this release, and a fleet of
physical Ubuntu/GPU combinations was not available. Energy is intentionally
`NOT_MEASURED` without a real Joule source. Therefore the global enterprise
acceptance status remains **INCOMPLETE**, with the remaining evidence gaps
listed in `docs/IMPLEMENTATION_STATUS.md`.

## Downloads

- `KR-Compiler-Android-IDE-v4.2.0.apk`
- `KR-Compiler-Windows-x64-v4.2.0.zip`
- `KR-Compiler-Linux-x64-v4.2.0.tar.xz`
- `KR-Compiler-Windows-toolchain-v4.2.0.zip`
- `KR-Compiler-Adaptive-Compute-example-v4.2.0.zip`
- `KR-Compiler-Adaptive-Compute-validation-v4.2.0.zip`
- `SHA256SUMS-v4.2.0.txt`
