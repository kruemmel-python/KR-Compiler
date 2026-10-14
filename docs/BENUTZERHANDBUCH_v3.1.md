# KR Compiler Android IDE v3.1 – Benutzerhandbuch

Dieses Handbuch zeigt den auf einem echten Android-Gerät geprüften Ablauf für ein gemeinsames grafisches C++-Projekt, das mit SDL3, Dear ImGui, CMake und vcpkg für Linux x64 und Windows x64 gebaut wird.

Die ausführliche Fassung mit Hinweisen und Fehlerbehebung steht auch als [Word-Handbuch](KR_Compiler_Android_Benutzerhandbuch_v3.1_15-10-2026.docx) bereit.

## 1. App öffnen

![Startansicht](images/v3.1/01-app-start.png)

KR Compiler starten und die Projektansicht öffnen.

## 2. GUI-Funktionen öffnen

![Projektmenü](images/v3.1/02-menue-gui-vcpkg.png)

Im Projektmenü stehen **Neues GUI-Projekt** und **vcpkg-Bibliotheken** bereit.

## 3. GUI-Projekt anlegen

![Dialog Neues GUI-Projekt](images/v3.1/03-neues-gui-projekt-dialog.png)

Einen Projektnamen eingeben und bestätigen. Die Vorlage erzeugt CMake, `vcpkg.json`, `src/main.cpp` sowie die Kennzeichnung als Desktop-GUI-Projekt.

## 4. Projektdateien prüfen

![Projektdateien](images/v3.1/04-projektdateien.png)

In der Dateiansicht `CMakeLists.txt`, `vcpkg.json`, `src/main.cpp` und `kr-gui-target.txt` kontrollieren.

## 5. vcpkg-Bibliotheken verwalten

![vcpkg-Bibliotheken](images/v3.1/05-vcpkg-bibliotheken.png)

Benötigte Pakete auswählen und speichern. Die Auswahl wird reproduzierbar in `vcpkg.json` festgehalten.

## 6. Linux x64 bauen

![Linux bereit](images/v3.1/06-linux-build-bereit.png)

Linux x64 auswählen und den Build starten.

![Linux Build läuft](images/v3.1/07-linux-build-laeuft.png)

CMake, vcpkg und Ninja werden automatisch eingerichtet.

![Linux Build erfolgreich](images/v3.1/08-linux-build-erfolgreich.png)

Der Gerätetest wurde erfolgreich abgeschlossen.

![Linux Artefakt](images/v3.1/09-linux-artefakt.png)

Ergebnis: `programm`, Linux x64 ELF Binary (Desktop), etwa 4,1 MB.

## 7. Windows x64 bauen

![Zielplattform auswählen](images/v3.1/10-zielplattform-waehlen.png)

Ohne Änderung am Projekt auf Windows x64 umstellen.

![Windows Build läuft](images/v3.1/11-windows-build-laeuft.png)

Den Build mit dem Windows-Cross-Compilerprofil starten.

![Windows Build erfolgreich](images/v3.1/12-windows-build-erfolgreich.png)

Auch der Windows-GUI-Build wurde erfolgreich abgeschlossen.

![Windows Artefakt](images/v3.1/13-windows-artefakt.png)

Ergebnis: `programm.exe`, Windows x64 Executable (Desktop), etwa 5,2 MB.

## Hinweise

- Windows- und Linux-Artefakte werden auf dem jeweiligen Desktop-Zielsystem ausgeführt, nicht auf Android.
- Beim erstmaligen Laden zusätzlicher vcpkg-Pakete ist eine Internetverbindung erforderlich.
- CMake-Projekte und vcpkg-Manifeste können Buildbefehle und Downloads auslösen. Nur vertrauenswürdige Projekte öffnen.
- Bei einem Fehler `build.log` und `build-result.json` in der Artefaktansicht prüfen oder teilen.

Teststand: KR Compiler Android IDE 3.1.0 auf RMX3853, 15. Oktober 2026.
