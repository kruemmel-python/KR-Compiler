# MDraft KR – Testprotokoll

Stand: 20. Oktober 2026, KR Compiler IDE v4.3.0

| Ziel | Ergebnis | Erzeugtes Artefakt |
|---|---|---|
| Windows x64 | Erfolgreich kompiliert, gelinkt und vier Sekunden als laufende GUI geprüft | `programm.exe`, 4.331.128 Bytes |
| Android ARM64 | Erfolgreich kompiliert, signiert und als GUI-Runner-APK geprüft | `programm-android-arm64.apk`, 7.169.129 Bytes |
| Linux x64 | Erfolgreich kompiliert, gelinkt und vier Sekunden als laufende GUI geprüft | `programm`, 2.321.936 Bytes; `libSDL3.so.0`, 3.288.792 Bytes |

Die Android-APK enthält nachweislich `LauncherActivity`,
`lib/arm64-v8a/libuser.so`, `lib/arm64-v8a/libSDL3.so` und den Marker
`assets/kr-gui-runner`. Zum Zeitpunkt der Prüfung war kein Android-Gerät
angeschlossen; der physische Starttest ist daher bewusst nicht als bestanden
eingetragen.

Alle drei Ergebnisse stammen aus derselben `.krproject` und demselben
Quellbaum. Es wurden keine Android-, Windows- oder Linux-spezifischen
Projektkopien angelegt.
