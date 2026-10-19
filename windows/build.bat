@echo off
setlocal
echo ========================================================
echo   Kompiliere KR Compiler IDE (Windows Desktop Edition)
echo ========================================================
cd /d "%~dp0"

if not exist bin mkdir bin
if not exist dist mkdir dist

if not exist "%~dp0runtime\jdk\bin\javac.exe" (
    echo [FEHLER] Portables JDK fehlt: runtime\jdk
    exit /b 2
)
powershell -NoProfile -Command "$files = (Get-ChildItem -Recurse '%~dp0src\*.java').FullName; & '%~dp0runtime\jdk\bin\javac.exe' -encoding UTF-8 -d '%~dp0bin' $files"
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] Kompilierung fehlgeschlagen!
    pause
    exit /b %ERRORLEVEL%
)

"%~dp0runtime\jdk\bin\jar.exe" cfm "%~dp0dist\KR-Compiler-IDE.jar" "%~dp0Manifest.txt" -C "%~dp0bin" .
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [FEHLER] JAR-Erstellung fehlgeschlagen!
    pause
    exit /b %ERRORLEVEL%
)

if not exist "%~dp0dist\components\cc-opencl" mkdir "%~dp0dist\components\cc-opencl"
xcopy /E /I /Y "%~dp0components\cc-opencl" "%~dp0dist\components\cc-opencl" >nul
if %ERRORLEVEL% NEQ 0 (
    echo [FEHLER] CC_OpenCL Enterprise konnte nicht in die portable Distribution kopiert werden.
    exit /b %ERRORLEVEL%
)

echo.
echo [ERFOLG] KR-Compiler-IDE.jar erfolgreich erstellt!
echo Pfad: %~dp0dist\KR-Compiler-IDE.jar
echo ========================================================
pause
