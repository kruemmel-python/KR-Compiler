"""Static portability regressions; does not replace device tests."""
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = (root / 'src' / 'CC_OpenCL.c').read_text()
cmake = (root / 'CMakeLists.txt').read_text()
assert 'has_fp64_support = (err == CL_SUCCESS && fp_config != 0);' in src
assert 'const char* cl_std = "CL1.2";' in src
assert '"-DENABLE_FAST_VARIANT=0"' in src
assert '#if defined(CL_HAS_FP64) && defined(cl_khr_fp64)' in src
assert 'if(ANDROID)' in cmake
assert 'KR_BUILD/CC_OpenCl.dll' not in cmake
print('PASS: Android/OpenCL portability source checks')

# FP32 kernel source must not introduce implicit double literals under -Werror.
for macro in ('ACCUM_CONST', 'REDUCE_ACCUM_CONST'):
    for literal in ('0.0', '1.0'):
        assert f'{macro}({literal})' not in src, (macro, literal)
print('PASS: FP32 accumulation literals')
