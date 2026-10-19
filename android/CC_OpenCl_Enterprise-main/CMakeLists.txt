cmake_minimum_required(VERSION 3.20)

project(CC_OpenCl
    VERSION 1.0.1
    DESCRIPTION "Standalone CC_OpenCl OpenCL driver DLL"
    LANGUAGES C CXX
)

# CC_OpenCL.c contains C++ raw-string literals for embedded OpenCL kernels: R"CLC(... )CLC"
# Therefore both C and C++ sources must be compiled as C++17.
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# ---------------------------------------------------------------------------
# 1. Windows x64 & LLVM/MinGW Toolchain Configuration (KR Compiler Android IDE)
# ---------------------------------------------------------------------------
# When targeting Windows with Clang/LLD (e.g. KR Compiler Android IDE cross-compiler),
# configure the runtime libraries explicitly so the MinGW driver does not request
# GCC runtime libraries (-lgcc, -lgcc_s) which are not present in the LLVM sysroot.
if(WIN32)
    if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
        set(_KR_LLVM_LINKER_FLAGS "-fuse-ld=lld -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind")
        set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} ${_KR_LLVM_LINKER_FLAGS}")
        set(CMAKE_MODULE_LINKER_FLAGS "${CMAKE_MODULE_LINKER_FLAGS} ${_KR_LLVM_LINKER_FLAGS}")
        set(CMAKE_EXE_LINKER_FLAGS "${CMAKE_EXE_LINKER_FLAGS} ${_KR_LLVM_LINKER_FLAGS}")
    endif()
endif()

# ---------------------------------------------------------------------------
# 2. OpenCL Dependency Resolution
# ---------------------------------------------------------------------------
# Try standard system / vcpkg find_package first.
if(NOT TARGET OpenCL::OpenCL)
    find_package(OpenCL QUIET)
endif()

# If OpenCL was not found in sysroot / system, use the vendored Khronos OpenCL
# headers and import library bundled directly with this repository.
if(NOT TARGET OpenCL::OpenCL AND UNIX AND NOT ANDROID)
    find_library(CC_OPENCL_SYSTEM_LOADER NAMES OpenCL libOpenCL.so.1)
    if(CC_OPENCL_SYSTEM_LOADER)
        add_library(OpenCL::OpenCL INTERFACE IMPORTED)
        target_include_directories(OpenCL::OpenCL INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/CL")
        target_link_libraries(OpenCL::OpenCL INTERFACE "${CC_OPENCL_SYSTEM_LOADER}")
    endif()
endif()
if(NOT TARGET OpenCL::OpenCL AND ANDROID)
    message(FATAL_ERROR "Android OpenCL loader not found. Supply an NDK-compatible OpenCL import library for the target device; Windows import libraries are not usable on Android.")
endif()
if(NOT TARGET OpenCL::OpenCL)
    message(STATUS "OpenCL was not located in sysroot; activating vendored Khronos OpenCL headers and import libraries.")
    add_library(OpenCL::OpenCL INTERFACE IMPORTED)
    target_include_directories(OpenCL::OpenCL INTERFACE
        ${CMAKE_CURRENT_SOURCE_DIR}/CL
    )
    if(MSVC)
        if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/build/OpenCL.lib")
            target_link_libraries(OpenCL::OpenCL INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/build/OpenCL.lib")
        elseif(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/CL/OpenCL.lib")
            target_link_libraries(OpenCL::OpenCL INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/CL/OpenCL.lib")
        endif()
    else()
        if(WIN32 AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/CL/libOpenCL.dll.a")
            target_link_libraries(OpenCL::OpenCL INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/CL/libOpenCL.dll.a")
        elseif(WIN32 AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/build/OpenCL.lib")
            target_link_libraries(OpenCL::OpenCL INTERFACE "${CMAKE_CURRENT_SOURCE_DIR}/build/OpenCL.lib")
        endif()
    endif()
endif()

# ---------------------------------------------------------------------------
# 3. Main Target: CC_OpenCl SHARED Library
# ---------------------------------------------------------------------------
add_library(CC_OpenCl SHARED
    src/CC_OpenCL.c
    src/CipherCore_NoiseCtrl.c
)

set_source_files_properties(
    src/CC_OpenCL.c
    src/CipherCore_NoiseCtrl.c
    PROPERTIES LANGUAGE CXX
)

target_include_directories(CC_OpenCl
    BEFORE PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/CL
)

target_link_libraries(CC_OpenCl
    PRIVATE
        OpenCL::OpenCL
)

target_compile_definitions(CC_OpenCl
    PRIVATE
        _CRT_SECURE_NO_WARNINGS
        CL_TARGET_OPENCL_VERSION=300
        CL_USE_DEPRECATED_OPENCL_1_2_APIS
)

if(MSVC)
    target_compile_options(CC_OpenCl PRIVATE /TP /W4 /WX)
else()
    target_compile_options(CC_OpenCl PRIVATE -Wall -Wextra -Werror)
    if(WIN32)
        target_link_libraries(CC_OpenCl PRIVATE -lkernel32 -luser32)
        if(CMAKE_CXX_COMPILER_ID MATCHES "Clang")
            target_link_options(CC_OpenCl PRIVATE -fuse-ld=lld -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind)
        endif()
    elseif(UNIX AND NOT APPLE)
        target_link_libraries(CC_OpenCl PRIVATE m dl pthread)
    endif()
endif()

# Set properties: Ensure exact output name CC_OpenCl.dll without 'lib' prefix on Windows
set_target_properties(CC_OpenCl PROPERTIES
    OUTPUT_NAME "CC_OpenCl"
    PREFIX ""
    IMPORT_PREFIX ""
    ARCHIVE_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/lib"
)
if(NOT DEFINED KR_EXECUTABLE_OUTPUT)
    set_target_properties(CC_OpenCl PROPERTIES
        RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
        LIBRARY_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
    )
endif()

# ---------------------------------------------------------------------------
# 4. Windows Test & Validation Executable (Satisfies KR Compiler & Test D)
# ---------------------------------------------------------------------------
if(EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/tests/test_cc_opencl.cpp")
    add_executable(test_cc_opencl tests/test_cc_opencl.cpp)
    target_include_directories(test_cc_opencl PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
        ${CMAKE_CURRENT_SOURCE_DIR}/include
        ${CMAKE_CURRENT_SOURCE_DIR}/CL
    )
    target_link_libraries(test_cc_opencl PRIVATE CC_OpenCl)
    # Test code is production code: warnings fail the build on every target.
    if(MSVC)
        target_compile_options(test_cc_opencl PRIVATE /W4 /WX)
    else()
        target_compile_options(test_cc_opencl PRIVATE -Wall -Wextra -Werror)
    endif()

    if(WIN32 AND CMAKE_CXX_COMPILER_ID MATCHES "Clang")
        target_link_options(test_cc_opencl PRIVATE -fuse-ld=lld -stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind)
    endif()
    if(NOT DEFINED KR_EXECUTABLE_OUTPUT)
        set_target_properties(test_cc_opencl PROPERTIES
            RUNTIME_OUTPUT_DIRECTORY "${CMAKE_BINARY_DIR}/bin"
        )
    endif()
endif()

# ---------------------------------------------------------------------------
# 5. Artifact Placement for KR Compiler IDE and Packaging
# ---------------------------------------------------------------------------
# KR_BUILD is owned by the KR IDE. Never write into it from an external CMake build.
# Artifacts remain in the CMake build directory and can be installed explicitly.
if(ANDROID)
    message(STATUS "Android ARM64: producing native libCC_OpenCl.so and test executable")
    set_target_properties(CC_OpenCl PROPERTIES PREFIX "lib" IMPORT_PREFIX "lib")
endif()

install(TARGETS CC_OpenCl
    RUNTIME DESTINATION bin
    LIBRARY DESTINATION bin
    ARCHIVE DESTINATION lib
)

if(TARGET test_cc_opencl)
    install(TARGETS test_cc_opencl RUNTIME DESTINATION bin)
endif()
