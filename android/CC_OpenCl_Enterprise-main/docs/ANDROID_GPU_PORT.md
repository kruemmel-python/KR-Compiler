# Android GPU compatibility patch

This revision addresses the observed Mali-G72 OpenCL compiler failure. The FP64 pragmas are conditional, FP64 detection no longer requires FMA, and strict builds do not silently enable relaxed math. The baseline kernel language is OpenCL C 1.2. Android does not use the Windows DLL packaging rules.

## Important deployment requirement

Android does not guarantee a public system `libOpenCL.so`. A compatible OpenCL loader/import library for the actual device must be supplied to CMake. The build intentionally refuses to link an Android binary against bundled Windows import libraries. Native OpenCL availability is vendor- and device-dependent.

## Validation

Build and run `test_cc_opencl` on the device. A complete GPU pass requires successful kernel compilation, execution and numerical checks; a host-only build cannot prove Android GPU compatibility. Keep the original DLL and public ABI unchanged on Windows.

## Remaining portability considerations

The legacy code has other optional OpenCL 2.x entry points and device-side enqueue code paths. These must be validated against the actual Android OpenCL loader and disabled if unsupported. Some kernels use vendor-dependent atomics. Not all Android GPUs provide OpenCL, and FP64 numerical equivalence is not promised on FP32-only devices.
