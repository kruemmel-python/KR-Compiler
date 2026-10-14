# Android-Implementierung

Die App liest ein C/C++-Projekt über Androids Storage Access Framework. Dadurch ist kein
pauschaler Zugriff auf den gesamten Gerätespeicher erforderlich. Die Ausgabe wird als
`KR_BUILD` in den ausgewählten Projektordner zurückgeschrieben.

## Buildwege

Enthält das Projekt eine `CMakeLists.txt`, führt die App eine echte CMake-Konfiguration und einen
Ninja-Build aus. Damit werden unter anderem Unterordner, Definitionen, Include-Pfade,
`target_link_libraries`, Bedingungen und `FetchContent` berücksichtigt. Ohne CMake werden
vorhandene `.c`, `.cc`, `.cpp` und `.cxx`-Dateien direkt als ein Programm gebaut.

Für Android wird ein CMake-`add_executable` intern in eine vom Runner ladbare ARM64-Bibliothek
überführt. Ressourcen aus dem Projekt werden in die erzeugte APK übernommen; beim Start ist das
Arbeitsverzeichnis der entpackte Ressourcenordner. Für Windows und Linux wird das ausgewählte
Ziel als `programm.exe` beziehungsweise `programm` exportiert.

## Enthaltene Laufzeit

Die Release-Laufzeit enthält CMake, Ninja, Git, `pkg-config`, CA-Zertifikate, Clang/LLVM sowie
Sysroots für Android ARM64, Windows x64 und Linux x64. Sie ist wegen ihrer Größe nicht Teil des
Git-Verlaufs. `scripts/download-toolchain.ps1` lädt die unveränderte ZIP-Datei aus Release
`v2.6.5` und verifiziert SHA-256
`36a62d8f8a20de53b217543cf5438f23000478ac97d6ed161a450f677d2cba5c`.

Wenn Online-Abhängigkeiten eingeschaltet sind, dürfen normale CMake-`FetchContent`-Deklarationen
und Git benötigte Quellen laden. Zusätzlich erkennt die App `find_package` für `fmt`,
`nlohmann_json`, `spdlog`, `Catch2` und `Eigen3`. Unbekannte oder proprietäre SDKs werden nicht
von beliebigen Quellen nachinstalliert; das Projekt muss eine vertrauenswürdige Bezugsquelle
angeben.

## Lokal bauen

```powershell
powershell -ExecutionPolicy Bypass -File .\android\scripts\build-apk.ps1
```

Das Skript prüft beziehungsweise lädt zuerst die Laufzeit, erzeugt einen lokalen
Entwicklungsschlüssel und startet anschließend den enthaltenen Gradle Wrapper. Benötigt werden:

- JDK 17 (`JAVA_HOME`)
- Android SDK API 35
- Android Build Tools
- Android NDK `27.0.12077973`
- CMake `3.22.1` aus dem Android SDK

Der lokale Schlüssel liegt unter `android/.local-signing`; die für den Laufzeit-Paketierer
exportierten Schlüsseldateien liegen nur im ignorierten Assets-Arbeitsordner. Offizielle Builds
können über die `KR_SIGNING_*`-Umgebungsvariablen eine andere Signatur verwenden.

`scripts/package-toolchain.ps1` ist das Maintainer-Skript, mit dem die Laufzeit aus Termux-Paketen
und den KR-Desktop-Sysroots erzeugt wird. Für normale Fork-Builds wird es nicht benötigt.
