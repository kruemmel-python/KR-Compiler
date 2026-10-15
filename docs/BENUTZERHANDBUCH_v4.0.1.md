# KR Compiler Android IDE v4.0.1 Benutzerhandbuch

Dieses Handbuch beschreibt den vollständigen Arbeitsablauf von der Erstellung eines neuen Projekts bis zur fertigen Android-APK, Windows-EXE oder Linux-ELF-Datei. Version 4.0.1 führt neben der **KR Unified Language (`.kr`)** erweiterte Unterstützung für **Enterprise C++ OpenCL-Bibliotheken** und gehärtete Windows-x64-Cross-Kompilierung ein.

Die ausführliche Fassung steht als [Word-Handbuch](KR_Compiler_Android_Benutzerhandbuch_v4.0_15-10-2026.docx) bereit.

---

## 1. Übersicht der Projektarten

KR Compiler Android IDE unterstützt vier Haupt-Projektkategorien:

| Projektkategorie | Vorlagen | Dateiformate | Besonderheit |
|---|---|---|---|
| **KR Unified Language (`.kr`)** | KR Konsole, KR GUI (SDL3), KR Sprachfusion Konsole, KR Sprachfusion GUI | `main.kr` | **C++20, Rust und Go frei in einer Datei kombiniert**; automatische FFI-Brücke |
| **Rust-Projekte** | Rust Konsole, Rust GUI mit SDL3 | `Cargo.toml`, `src/main.rs` | Standard Cargo- & rustc-Buildpfade |
| **Go-Projekte** | Go Konsole, Go GUI mit SDL3 | `go.mod`, `main.go` | go build mit CGO- und SDL3-Anbindung |
| **C/C++ & Enterprise GPU** | Konsolen- & GUI-Projekte, OpenCL, vcpkg | `CMakeLists.txt`, `.cpp`, `.c`, `.h` | Clang, CMake, Ninja, vcpkg-Manifest, OpenCL 3.0 Fallback |

---

## 2. Neues Projekt anlegen

1. **Menü öffnen**: Tippe in der KR Compiler App oben rechts auf die drei Punkte.
2. **Vorlage auswählen**:
   - Für **KR Unified Language**: Wähle **Neues .kr-Projekt** und wähle z. B. *KR Sprachfusion (C++ / Rust / Go)* oder *KR GUI mit SDL3*.
   - Für **reines Rust / Go**: Wähle **Neues Rust-/Go-Projekt** und bestimme Sprache und Ausgabetyp.
   - Für **C/C++ & GPU-Bibliotheken**: Öffne ein bestehendes CMake-Repository (z. B. via GitHub-Klon) oder erstelle ein C++-Projekt.
3. **Projektname eingeben**: Vergib einen aussagekräftigen Namen (z. B. `mein-kr-projekt`).
4. **Erstellen**: Die IDE generiert den Projektordner inklusive aller Startdateien und öffnet die Hauptdatei sofort im Editor.

| Projektmenü | Projekttyp wählen |
|---|---|
| ![Projektmenü](images/v4.0/02-project-menu.png) | ![Rust und Go auswählen](images/v4.0/03-language-select.png) |

| Projektname | Zielsystem |
|---|---|
| ![Projektname vergeben](images/v4.0/04-project-name.png) | ![Zielsystem auswählen](images/v4.0/05-target-select.png) |

---

## 3. Quelltext in der KR Unified Language (`.kr`) bearbeiten

In einer `.kr`-Datei kannst du beliebig zwischen C++20, Rust und Go wechseln, **ohne** künstliche Annotationen oder Marker wie `@cpp`, `@rust` oder `@go`.

### Beispiel: Dreifache Sprachfusion in einer Datei
```cpp
#include <iostream>
#include <cstdint>

// --- C++20 Bereich ---
int cpp_calculate(int a, int b) {
    return a * b + 42;
}

// --- Rust Bereich ---
fn rust_verify(val: i32) -> bool {
    let ok = val > 50;
    if ok { go_notify(val); }
    ok
}

// --- Go Bereich ---
func go_notify(val int32) {
    println("Go empfaengt Benachrichtigung von Rust: Wert =", val)
}

// --- Gemeinsamer Einstiegspunkt (C++) ---
int main() {
    int res = cpp_calculate(3, 4);
    bool check = rust_verify(res);
    std::cout << "Ergebnis: " << res << ", Verifiziert: " << check << std::endl;
    return 0;
}
```

*Hinweis*: Für detaillierte Programmierregeln, Speichermodelle und ABI-Typen beachte das [KR Unified Language Entwickler-Handbuch](KR_UNIFIED_LANGUAGE_GUIDE.md).

---

## 4. Enterprise OpenCL- & Windows-x64-Kompilierung (Neu in v4.0.1)

KR IDE v4.0.1 ermöglicht die vollständige Cross-Kompilierung komplexer Windows-C++-Bibliotheken und OpenCL-Anwendungen direkt auf Android:

1. **LLVM-MinGW Toolchain**: Nutzt Clang 21.1.8 und LLD. Durch das Setzen der LLVM-Laufzeit (`-stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind`) werden GCC-Laufzeitabhängigkeiten vermieden.
2. **OpenCL-Integration**: Bei fehlendem System-SDK greift das Projekt transparent auf mitgeführte Khronos OpenCL 3.0 Header und MinGW-Importbibliotheken (`CL/libOpenCL.dll.a`) zurück.
3. **Ausgabe-Artefakte**: In `KR_BUILD/` entstehen direkt die Windows-DLL (`CC_OpenCl.dll`) und die Importbibliothek (`libCC_OpenCl.dll.a`).

---

## 5. Zielsystem auswählen und kompilieren

In der **Build-Ansicht** stehen drei Zielplattformen bereit:

| Zielplattform | Erzeugtes Artefakt | Verwendung |
|---|---|---|
| **Android APK ARM64** | `projekt-android-arm64.apk` | Direkt auf dem Smartphone installieren und ausführen |
| **Windows EXE / DLL x64** | `programm.exe` / `.dll` | Auf Windows x64 übertragen und per Doppelklick / CMD starten |
| **Linux Programm x64** | `programm` (ELF) | Auf Linux x64 kopieren, `chmod +x` setzen und ausführen |

1. Wähle das gewünschte Zielsystem aus dem Dropdown-Menü.
2. Tippe auf **Kompilieren**.
3. Verfolge den Fortschritt im Live-Buildprotokoll.

---

## 6. Artefakte verwalten und ausführen

Nach erfolgreichem Build wechselst du in den Reiter **Artefakte**:

- **Android APK**: Ein Fingertipp auf **Installieren** startet den Android-Paket-Installer. Nach der Installation kannst du die App direkt öffnen.
- **Windows- & Linux-Dateien**: Können direkt geteilt, auf den internen Speicher exportiert oder über USB / ADB auf den Ziel-PC kopiert werden.

---

## 7. Fehlerbehebung

| Problem | Ursache | Lösung |
|---|---|---|
| `unable to find library -lgcc_s` | MinGW-Clang sucht GCC-Runtime | In `CMakeLists.txt` LLVM-Linkerflags ergänzen (`-stdlib=libc++ -rtlib=compiler-rt -unwindlib=libunwind`) |
| `Could NOT find OpenCL` | Kein Host-OpenCL im Sysroot | Vendored Khronos-Header und Import-Bibliothek `CL/libOpenCL.dll.a` einbinden |
| Erster Build dauert länger | Initialisierung der Toolchains | Einmaliger Vorgang; Folgebilds nutzen inkrementellen Buildcache |
| APK-Installation blockiert | Fehlende Berechtigung | Installation aus KR Compiler in den Android-Einstellungen erlauben |
