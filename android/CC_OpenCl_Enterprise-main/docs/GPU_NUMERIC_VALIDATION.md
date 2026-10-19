# GPU numeric validation – cross-platform

The existing `test_cc_opencl` runner now performs seven deterministic GPU-versus-CPU reference checks **after** its five original smoke tests and **before** driver shutdown. It uses the production exported driver API, real OpenCL allocations, uploads, dispatches and downloads.

Coverage: `add_elementwise` (257 elements), `mul_elementwise` (257), `gelu_elementwise` (257), `matrix_multiply` (2x3 times 3x2), `transpose` (2x3), `softmax_rowwise` (two rows), `layer_norm` (two rows including a constant row). Each case checks the dispatch, transfers, finite results, absolute and relative error. Failure details include the first mismatching index and expected/actual values. Validation summary is included in `cc_opencl_execution.log` via the existing console tee. A failed validation produces a nonzero runner exit status.

**Scope:** Seven of the 50 registered core kernels have numeric tests in this release. The other 43 have *not* been numerically validated by this suite, including SubQG. Kernel compilation is not execution verification. The 5/5 smoke-test score is separate from numeric validation results. Strict/Fast build paths and FP32/FP64 selection are unchanged.

Build using the existing CMake project on Windows x64, Linux x64, Android ARM64. Execute `test_cc_opencl` through the KR IDE runner on Android; see `cc_opencl_execution.log` in Downloads if Android permits access. An OpenCL device is required to pass the numeric suite. This release was compiled on Linux x64; Windows/Android execution has not been verified here.
