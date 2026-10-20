# MDraft KR – Testprotokoll

Stand: 20. Oktober 2026, KR Compiler IDE v4.2.2

| Ziel | Ergebnis | Erzeugtes Artefakt |
|---|---|---|
| Windows x64 | Erfolgreich kompiliert, gelinkt und vier Sekunden als laufende GUI geprüft | `programm.exe`, 4.331.128 Bytes |
| Android ARM64 | Erfolgreich kompiliert, signiert und als GUI-Runner-APK geprüft | `programm-android-arm64.apk`, 7.169.129 Bytes |
| Linux x64 | KR-Quellen werden generiert; der Link-Build stoppt, weil im aktuellen portablen Linux-v4.2.2-Bestand SDL3-Header und `libSDL3` fehlen | kein Artefakt |

Die Android-APK enthält nachweislich `LauncherActivity`,
`lib/arm64-v8a/libuser.so`, `lib/arm64-v8a/libSDL3.so` und den Marker
`assets/kr-gui-runner`. Zum Zeitpunkt der Prüfung war kein Android-Gerät
angeschlossen; der physische Starttest ist daher bewusst nicht als bestanden
eingetragen.

Der Linux-Befund betrifft die Auslieferung der IDE-Toolchain, nicht einen
zweiten Projektquellbaum. Sobald die Linux-IDE SDL3 unter
`toolchain/include/SDL3` und `toolchain/lib` mitliefert, wird dasselbe
`.krproject` ohne Quelländerung für Linux gebaut.
