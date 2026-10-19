@echo off
title KR Compiler IDE - Windows Desktop Edition (Console Log)
cd /d "%~dp0"
if not exist "%~dp0toolchain\kr-toolchain.stamp" exit /b 2
if not exist "%~dp0runtime\jdk\bin\java.exe" exit /b 2

if exist "%~dp0toolchain\bin" set "PATH=%~dp0toolchain\bin;%PATH%"
"%~dp0runtime\jdk\bin\java.exe" -jar "%~dp0dist\KR-Compiler-IDE.jar"
pause
