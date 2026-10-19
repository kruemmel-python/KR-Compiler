@echo off
title KR Compiler IDE - Windows Desktop Edition
cd /d "%~dp0"

if not exist "%~dp0toolchain\kr-toolchain.stamp" (
    echo [FEHLER] Portable Compiler-Toolchain fehlt. Distribution ist unvollstaendig.
    pause
    exit /b 2
)
if not exist "%~dp0runtime\jdk\bin\javaw.exe" (
    echo [FEHLER] Portable Java-Laufzeit fehlt. Distribution ist unvollstaendig.
    pause
    exit /b 2
)

if exist "%~dp0toolchain\bin" set "PATH=%~dp0toolchain\bin;%PATH%"
start "" "%~dp0runtime\jdk\bin\javaw.exe" -jar "%~dp0dist\KR-Compiler-IDE.jar"
