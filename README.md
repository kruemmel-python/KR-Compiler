# KR Compiler IDE

KR Compiler IDE ist eine portable Entwicklungsumgebung für **KR Unified
Language (`.kr`)**, **C++20**, **Rust** und **Go**. Sie erzeugt Programme für
Android ARM64, Windows x64 und Linux x64. Die veröffentlichten Pakete enthalten
die benötigten Compiler, Laufzeiten und SDK-Bestandteile; beim normalen Betrieb
müssen keine zusätzlichen SDKs heruntergeladen werden.

## Aktuelle Version

Aktueller Stand: **v4.3.0**

[Release v4.3.0 herunterladen](https://github.com/kruemmel-python/KR-Compiler/releases/tag/v4.3.0)

| Plattform | Release-Datei |
|---|---|
| Android ARM64 | `KR-Compiler-Android-IDE-v4.3.0.apk` |
| Android-Buildtoolchain | `KR-Compiler-Android-toolchain-v4.3.0.zip` |
| Windows x64 | `KR-Compiler-Windows-x64-v4.3.0.zip` |
| Linux x64 | `KR-Compiler-Linux-x64-v4.3.0.tar.xz` |
| Plattformübergreifendes SDL3-Beispiel | `MDraft-KR-CrossPlatform-v4.3.0.zip` |
| Automatisches Compute-Beispiel | `KR-AUTO-COMPUTE-TEST-v4.3.0.zip` |
| 16-Thread-GPU-Stresstest | `KR-16-PARALLEL-GPU-STRESS-v4.3.0.zip` |
| Prüfsummen | `SHA256SUMS-v4.3.0.txt` |

Ältere Binärstände und deren Hinweise bleiben in den
[GitHub-Releases](https://github.com/kruemmel-python/KR-Compiler/releases)
abrufbar. Das Repository dokumentiert bewusst nur den aktuellen Stand.

## Was die IDE unterstützt

- `.kr`-Projekte mit C++20, Rust und Go sowie automatischen C-ABI-Brücken
- Android-APK, Windows-EXE und Linux-ELF als Zielartefakte
- CMake-, C/C++-, Cargo- und Go-Projekte
- portable Windows-, Linux- und Android-Compilerlaufzeiten
- SDL3-Projekte mit vollständig eingebetteten Headern, Bibliotheken und
  Laufzeitdateien für Android, Windows und Linux
- sichere Quellzuordnung für Diagnosen aus generierten Dateien
- integrierten, freigabepflichtigen Programmieragenten
- Adaptive Compute mit CPU/GPU-Dispatcher, Telemetrie und Validierung

## Automatisches Adaptive Compute

Der Compiler erkennt kanonische, unabhängige FP32-Schleifen für
Vektoraddition und Vektormultiplikation und wandelt sie automatisch in Aufrufe
der vorhandenen CC_OpenCL-Runtime um. Das Quellprogramm benötigt weder
`kr_compute_*`-Aufrufe noch OpenCL-Code.

Die Modi `AUTO`, `CPU_ONLY`, `GPU_ONLY` und `VERIFY` werden in der
`.krproject`-Datei gewählt. Unsichere oder nicht eindeutig typisierte Schleifen
bleiben unverändert auf dem ursprünglichen CPU-Pfad. `GPU_ONLY` darf bei einer
vorgesehenen GPU-Operation nicht still auf CPU zurückfallen.

Das vollständige, kompilierbare Beispiel liegt unter
[`KR_AUTO_COMPUTE_TEST`](KR_AUTO_COMPUTE_TEST/).

## Schnellstart

1. Das aktuelle Paket für die eigene Plattform aus dem Release laden.
2. Archiv entpacken beziehungsweise die Android-APK installieren.
3. Einen Projektordner mit **Ordner öffnen** auswählen.
4. Zielplattform und Compute-Modus festlegen.
5. **Kompilieren** wählen; Ausgaben entstehen im Projektordner `KR_BUILD`.

## Repository-Struktur

| Pfad | Inhalt |
|---|---|
| `android/` | Android-App, Runner, Gradle-Build und mobile Toolchain-Integration |
| `windows/` | eigenständige portable Windows-IDE |
| `linux/` | eigenständige portable Linux-IDE |
| `KR_AUTO_COMPUTE_TEST/` | aktuelles Ende-zu-Ende-Beispiel |
| `examples/MDraft_KR_CrossPlatform/` | ein modulares SDL3-GUI-Projekt für alle drei Ziele |
| `docs/` | ausschließlich aktuelle Benutzer- und Entwicklerdokumentation |
| `tests/` | plattformübergreifende Validierungswerkzeuge |

## Aus dem Quellcode bauen

Für einen lokalen Android-Build werden JDK 17, Android SDK API 35, Build Tools
35, NDK und CMake benötigt. Build-Caches und große Toolchain-Dateien sind nicht
Teil des Git-Verlaufs.

```powershell
git clone https://github.com/kruemmel-python/KR-Compiler.git
cd KR-Compiler
powershell -ExecutionPolicy Bypass -File .\android\scripts\build-apk.ps1
```

Die Desktop-Quellen werden mit den jeweiligen portablen Paketen gebaut:

```text
windows/build.bat
linux/build.sh
```

## Dokumentation

- [Vollständige Projektdokumentation](docs/PROJECT_DOCUMENTATION.md)
- [Benutzerhandbuch](docs/USER_GUIDE.md)
- [KR Unified Language](docs/KR_UNIFIED_LANGUAGE_GUIDE.md)
- [Adaptive Compute](docs/ADAPTIVE_COMPUTE.md)
- [Portable Linux-x64-Distribution](docs/PORTABLE_LINUX_X64.md)
- [Build- und Release-Artefakte](docs/BUILD_ARTIFACTS.md)
- [Implementierungsstatus und bekannte Grenzen](docs/IMPLEMENTATION_STATUS.md)
- [Aktuelle Release-Hinweise](RELEASE_NOTES.md)

## Lizenz

Der KR-Compiler-Quellcode steht unter GPL-3.0. Hinweise zu eingebetteten
Komponenten stehen in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
