@echo off
title KR Compiler IDE - Windows Desktop Edition
cd /d "%~dp0"

if not exist "%~dp0toolchain\kr-toolchain.stamp" (
    echo [INFO] Pruefe integrierte Compiler-Toolchain...
    if exist "%~dp0download-toolchain.ps1" (
        powershell -ExecutionPolicy Bypass -File "%~dp0download-toolchain.ps1"
    )
)

if exist "%~dp0toolchain\bin" set "PATH=%~dp0toolchain\bin;%PATH%"
start javaw -jar "%~dp0dist\KR-Compiler-IDE.jar"
