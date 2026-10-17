@echo off
title KR Compiler IDE (Konsole)
cd /d "%~dp0"
java -jar "%~dp0dist\KR-Compiler-IDE.jar"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Prozess beendet mit Fehlercode %ERRORLEVEL%.
    pause
)
