# Portable Linux-x64 distribution

## Delivered layout

The standalone Linux IDE is in `linux/`. It uses the same KR frontend,
adaptive-compute analyzer and desktop editor architecture as the Windows IDE.
It does not use compilers, Java, Rust, Go, CMake or the Android SDK from the
host system.

Bundled components:

- Eclipse Temurin JDK 21.0.12+1
- LLVM/Clang/LLD 21.1.8 and a Linux x64 sysroot
- CMake 4.4.4 and Ninja 1.13.1
- Go 1.27.1
- Rust 1.99.1 with Linux, Windows GNU and Android ARM64 standard libraries
- Android NDK r30
- Windows x64 headers, libc++ and import/static libraries
- Ubuntu OpenCL ICD loader plus Khronos OpenCL headers
- Full CC_OpenCL Enterprise sources and kernel inventory

The hardware-specific OpenCL implementation is intentionally not bundled.
The included ICD loader discovers the GPU implementation supplied by the
installed graphics driver. If no OpenCL platform exists, applications must use
the CPU path; no proprietary driver is downloaded.

## Offline policy

`Start-KR-IDE.sh`, `build.sh` and the IDE build engine only accept tools below
`linux/toolchain`. Missing bundle content is a hard error. No runtime download
or silent fallback to `/usr/bin`, a system JDK or a system SDK is performed.

The one-time distribution provisioning command is:

```sh
linux/scripts/provision-toolchain.sh
```

It pins and verifies all manufacturer archives. Its download, temporary,
Rust, Go and XDG caches are forced below `.linux-toolchain-cache` in the
project, so a full system drive is not used.

## Verification

Run:

```sh
linux/scripts/verify-portable-toolchain.sh
```

The test creates and executes Linux C++20, Rust and Go programs, creates a
Windows x64 PE executable, creates an Android ARM64 shared library, compiles
and loads CC_OpenCL on Linux, and rebuilds the Linux IDE JAR with the bundled
JDK. All outputs and temporary files stay below `linux/.verification`.

Verified environment: Ubuntu 24.04 under WSL2, x86-64. Supported release
baseline is Ubuntu 22.04 or newer on x86-64. A literal guarantee for every
historic or future Ubuntu release is not technically possible because glibc,
kernel, graphics-driver and display-server ABIs differ.

