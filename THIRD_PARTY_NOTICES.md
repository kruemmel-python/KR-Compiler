# Hinweise zu Drittkomponenten

KR Compiler verwendet oder bündelt Open-Source-Komponenten, die ihren jeweiligen Lizenzen
unterliegen. Dazu gehören insbesondere:

- Android Gradle Plugin und Android `apksig` (Apache License 2.0)
- Android NDK/CMake-Laufzeitkomponenten (Android Open Source Project-Lizenzen)
- Termux-Pakete für Clang/LLVM, CMake, Ninja, Git, pkg-config, Dash und CA-Zertifikate
- MinGW-/libc++-Komponenten für das Windows-x64-Ziel
- GNU-/glibc-Komponenten für das Linux-x64-Ziel
- SDL 3.4.18 als Quell-, Header- und Binärbestand für Android ARM64, Windows
  x64 und Linux x64 sowie das Android-Java-Frontend im Runner-Template
  (zlib-Lizenz; vollständiger Text unter `android/runner/SDL3-LICENSE.txt`
  und in den Toolchain-Lizenzordnern)

Die vollständigen Paket-Metadaten und Lizenztexte der eingebetteten Werkzeuge werden zusammen
mit den Komponenten in der Compilerlaufzeit ausgeliefert, insbesondere unter den jeweiligen
`share/doc`- und Lizenzpfaden. Die GPL-3.0-Lizenz des KR-Compiler-Quellcodes ersetzt diese
Lizenzen nicht.

Das Projekt lädt bei aktivierter Online-Option gegebenenfalls weitere Bibliotheken anhand der
CMake-Konfiguration des ausgewählten Benutzerprojekts. Für deren Lizenzen und Einsatzbedingungen
ist das jeweilige Projekt verantwortlich.
