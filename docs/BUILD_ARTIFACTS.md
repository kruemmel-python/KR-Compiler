# Verified release artifacts

Release: `v4.1.1`
Build date: 2026-10-19 (Europe/Berlin)

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `KR-Compiler-Adaptive-Compute-example-v4.1.1.zip` | 1729 | `71a6b18e144319c62cabc7616ba4f330a93ece65e82e68d8dc00fd26224f9bc8` |
| `KR-Compiler-Android-IDE-v4.1.1.apk` | 744277750 | `474db531526b30fcb06c0eb8029aaeba818ed4483c2cd61d1c4e4543e6ff6e78` |
| `KR-Compiler-Linux-x64-v4.1.1.tar.xz` | 1103060828 | `b1adafe60fffea96cc237ef2c7bc672574c000b178d3a5d859b102c7fa24d370` |
| `KR-Compiler-Windows-toolchain-v4.1.1.zip` | 665432516 | `38d32d05ae4956457b50a17e7ab38e0b67adc7e54ae5292a5342a649552bc23e` |
| `KR-Compiler-Windows-x64-v4.1.1.zip` | 875185581 | `2e770390f5a0c4907486c3810436eeb066938fb493a3ae26b2c293854188dfb1` |

The Android APK is a signed release build containing its compiler payload.
The Linux and Windows archives contain their Java runtime and compiler
toolchains and do not download SDKs when the IDE starts. The separate Windows
toolchain ZIP now contains the complete GCC, Android, Go and Rust payload.

## Verification performed

- Android clean `testDebugUnitTest assembleRelease`: passed (146 tasks).
- Android release metadata: version code 411, version name 4.1.1.
- Adaptive Compute example: compiled and ran; analyzer reports the first loop
  as `ELEMENTWISE_ADD` and `GPU_ELIGIBLE`.
- Linux portable smoke suite: native C++20, Rust and Go passed; Windows x64
  PE32+ and Android ARM64 ELF cross-builds passed; CC_OpenCL no-device handling
  passed.
- Windows portable package: bundled Temurin 21, GCC 16.1.0, Go 1.27.0 and
  Rust 1.88.0 started successfully.
- Final ZIP and `tar.xz` files were reopened and checked for launchers,
  runtimes, compilers and IDE JARs.
