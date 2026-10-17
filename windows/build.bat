@echo off
setlocal
echo ========================================================
echo   Kompiliere KR Compiler IDE (Windows Desktop Edition)
echo ========================================================
cd /d "%~dp0"

if not exist bin mkdir bin
if not exist dist mkdir dist

powershell -NoProfile -Command "$files = (Get-ChildItem -Recurse '%~dp0src\*.java').FullName; & javac -encoding UTF-8 -d '%~dp0bin' $files"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] Kompilierung fehlgeschlagen!
    pause
    exit /b %ERRORLEVEL%
)

jar cfm "%~dp0dist\KR-Compiler-IDE.jar" "%~dp0Manifest.txt" -C "%~dp0bin" .
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] JAR-Erstellung fehlgeschlagen!
    pause
    exit /b %ERRORLEVEL%
)

echo.
echo [ERFOLG] KR-Compiler-IDE.jar erfolgreich erstellt!
echo Pfad: %~dp0dist\KR-Compiler-IDE.jar
echo ========================================================
pause
