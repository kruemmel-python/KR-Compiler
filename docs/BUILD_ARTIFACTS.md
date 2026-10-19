# Verified release artifacts

Release: `v4.1.0`  
Build date: 2026-10-19 (Europe/Berlin)

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `KR-Compiler-Android-IDE-v4.1.0.apk` | 744276650 | `00ff9985894bbdb7a7b430ec6fb39c1f43a048b0736f37c5929c97e80bac8ee1` |
| `KR-Compiler-Linux-x64-v4.1.0.tar.xz` | 1103125140 | `5bdad61df7575465c5dd4e04cd0e6d88f3a5dc6e6a7a4820bae776ee8d92ae40` |
| `KR-Compiler-Windows-toolchain-v4.1.0.zip` | 82781805 | `a15331365cf68b01b5bfaaa837718596c0bd3b7944fcab330dc7f17b4511d428` |
| `KR-Compiler-Windows-x64-v4.1.0.zip` | 875185481 | `92bedadd88542e4be9fa49fd39708000a1fd06c376cdf832631278126c21cc98` |

The Android APK is a signed release build containing its compiler payload.
The Linux and Windows archives contain their Java runtime and compiler
toolchains and do not download SDKs when the IDE starts. The separate Windows
toolchain ZIP is provided for the repository bootstrap script and recovery of
an incomplete local checkout.

## Verification performed

- Android `clean testDebugUnitTest assembleRelease`: passed (146 tasks).
- Linux portable smoke suite: native C++20, Rust and Go passed; Windows x64
  PE32+ and Android ARM64 ELF cross-builds passed; CC_OpenCL no-device handling
  passed.
- Windows portable package: bundled Temurin 21, GCC 16.1.0, Go 1.27.0 and
  Rust 1.88.0 started successfully; the IDE JAR rebuilt successfully.
- The final Windows ZIP and Linux `tar.xz` were reopened and checked for the
  launchers, Java runtime, compilers and IDE JAR.
