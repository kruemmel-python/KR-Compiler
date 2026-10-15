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

Grafische Projekte können über „Neues GUI-Projekt“ als SDL3-/Dear-ImGui-Vorlage erzeugt
werden. „vcpkg-Bibliotheken“ pflegt das Manifest `vcpkg.json`. Beim Build bindet die App die
integrierte vcpkg-Toolchain ein. `kr-gui-target.txt` kennzeichnet ein GUI-Projekt. Windows wird
ohne Konsolenfenster, Linux gegen die bereitgestellten X11/Xext-Grundlagen und Android als native
Bibliothek für einen SDLActivity-Runner mit Touch und OpenGL ES gebaut.

Ab Version 3.2 setzt der Android-CMake-Pfad `ANDROID` und die ABI ausdrücklich. SDL3 verwendet
damit sein mobiles Backend statt X11 oder Wayland. Ein reproduzierbares kleines Headerpaket ergänzt
EGL, GLES, GLES2, GLES3 und KHR aus dem offiziellen Android-NDK. Der Launcher erkennt GUI-Projekte
über einen Paketmarker und startet wahlweise den SDLActivity- oder den bisherigen Konsolenrunner.

## Enthaltene Laufzeit

Die Release-Laufzeit enthält CMake, Ninja, Git, `pkg-config`, CA-Zertifikate, Clang/LLVM sowie
Sysroots für Android ARM64, Windows x64 und Linux x64. Das Linux-Profil enthält ab Version 3.1
zusätzlich die X11/Xext-Bestandteile für grafische Programme. Die Laufzeit ist wegen ihrer Größe
nicht Teil des Git-Verlaufs. `scripts/download-toolchain.ps1` lädt die unveränderte ZIP-Datei aus
Release `v3.1.0` und verifiziert SHA-256
`bb1b24d80d7ffbbcbe24e96ada527370f97bd9d169a97ffcd9d78b9670810b08`.

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

`scripts/package-android-graphics-headers.ps1` erzeugt das kleine Android-Grafikheaderpaket
reproduzierbar aus dem lokal installierten NDK. Für normale Fork-Builds ist dieser Schritt nicht
erforderlich, da das geprüfte Paket bereits als App-Asset enthalten ist.

Der lokale Schlüssel liegt unter `android/.local-signing`; die für den Laufzeit-Paketierer
exportierten Schlüsseldateien liegen nur im ignorierten Assets-Arbeitsordner. Offizielle Builds
können über die `KR_SIGNING_*`-Umgebungsvariablen eine andere Signatur verwenden.

`scripts/package-toolchain.ps1` ist das Maintainer-Skript, mit dem die Laufzeit aus Termux-Paketen
und den KR-Desktop-Sysroots erzeugt wird. `scripts/prepare-linux-gui-sysroot.ps1` ergänzt dafür die
Linux-X11/Xext-Dateien. Für normale Fork-Builds werden beide Skripte nicht benötigt.
