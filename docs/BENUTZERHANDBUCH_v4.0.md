# KR Compiler Android IDE v4.0 Benutzerhandbuch

Dieses Handbuch beschreibt den vollständigen Arbeitsablauf von der Erstellung eines neuen Projekts bis zur fertigen Android-APK, Windows-EXE oder Linux-ELF-Datei. Version 4.0.0 führt die **KR Unified Language (`.kr`)** ein und bietet native Unterstützung für C++20, Rust und Go.

Die ausführliche Fassung steht als [Word-Handbuch](KR_Compiler_Android_Benutzerhandbuch_v4.0_15-10-2026.docx) bereit.

---

## 1. Übersicht der Projektarten

KR Compiler Android IDE unterstützt drei Haupt-Projektkategorien:

| Projektkategorie | Vorlagen | Dateiformate | Besonderheit |
|---|---|---|---|
| **KR Unified Language (`.kr`)** | KR Konsole, KR GUI (SDL3), KR Sprachfusion Konsole, KR Sprachfusion GUI | `main.kr` | **C++20, Rust und Go frei in einer Datei kombiniert**; automatische FFI-Brücke |
| **Rust-Projekte** | Rust Konsole, Rust GUI mit SDL3 | `Cargo.toml`, `src/main.rs` | Standard Cargo- & rustc-Buildpfade |
| **Go-Projekte** | Go Konsole, Go GUI mit SDL3 | `go.mod`, `main.go` | go build mit CGO- und SDL3-Anbindung |
| **C/C++ Projekte** | Konsolen- & GUI-Projekte, vcpkg | `CMakeLists.txt`, `.cpp`, `.h` | Clang, CMake, Ninja, vcpkg-Manifest |

---

## 2. Neues Projekt anlegen

1. **Menü öffnen**: Tippe in der KR Compiler App oben rechts auf die drei Punkte.
2. **Vorlage auswählen**:
   - Für **KR Unified Language**: Wähle **Neues .kr-Projekt** und wähle z. B. *KR Sprachfusion (C++ / Rust / Go)* oder *KR GUI mit SDL3*.
   - Für **reines Rust / Go**: Wähle **Neues Rust-/Go-Projekt** und bestimme Sprache und Ausgabetyp.
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

## 4. Zielsystem auswählen und kompilieren

In der **Build-Ansicht** stehen drei Zielplattformen bereit:

| Zielplattform | Erzeugtes Artefakt | Verwendung |
|---|---|---|
| **Android APK ARM64** | `projekt-android-arm64.apk` | Direkt auf dem Smartphone installieren und ausführen |
| **Windows EXE x64** | `programm.exe` | Auf Windows x64 übertragen und per Doppelklick / CMD starten |
| **Linux Programm x64** | `programm` (ELF) | Auf Linux x64 kopieren, `chmod +x` setzen und ausführen |

1. Wähle das gewünschte Zielsystem aus dem Dropdown-Menü.
2. Tippe auf **Kompilieren**.
3. Verfolge den Fortschritt im Live-Buildprotokoll: Die IDE orchestriert Clang++, Cargo und go build automatisch.

---

## 5. Artefakte verwalten und ausführen

Nach erfolgreichem Build wechselst du in den Reiter **Artefakte**:

- **Android APK**: Ein Fingertipp auf **Installieren** startet den Android-Paket-Installer. Nach der Installation kannst du die App direkt öffnen.
- **Konsolen-Apps auf Android**: Werden im integrierten Text-Runner gestartet und zeigen Konsolenausgaben in Echtzeit.
- **GUI-Apps auf Android**: Öffnen die hardwarebeschleunigte SDL3-Oberfläche über die native Android-Runner-Aktivität.
- **Windows- & Linux-Dateien**: Können über die Schaltfläche **Teilen** per E-Mail, Cloud oder USB an den Desktop-PC übertragen werden.

---

## 6. Screenshots der Beispielanwendungen

| Rust Konsole auf Android | Go Konsole auf Android |
|---|---|
| ![Rust Konsolenprogramm auf Android](images/v4.0/rust-console.png) | ![Go Konsolenprogramm auf Android](images/v4.0/go-console.png) |

| Rust GUI mit SDL3 auf Android | Go GUI mit SDL3 auf Android |
|---|---|
| ![Rust GUI auf Android](images/v4.0/rust-gui.png) | ![Go GUI auf Android](images/v4.0/go-gui.png) |

---

## 7. Querformat und TV-Spiegelung

Wenn du dein Android-Gerät drehst (Querformat), passt sich die IDE automatisch an:
- Kopf- und Statusleisten werden komprimiert.
- Die Build-Leiste wird im Editor minimiert, sodass auch bei kleineren Bildschirmen oder TV-Spiegelung über USB-C/HDMI maximaler Platz für Code bleibt.
- Tastaturbedienung mit externer Bluetooth- oder USB-Tastatur wird vollständig unterstützt.

---

## 8. Fehlerbehebung & Häufige Fragen

| Situation | Ursache & Lösung |
|---|---|
| **Erster Build dauert länger** | Beim ersten Start werden die Offline-Compiler und Sysroots eingerichtet. Spätere Builds verwenden den schnellen Cache. |
| **`use of undeclared identifier` in `.kr`** | Stelle sicher, dass die aufgerufene Funktion in der Zielsprache existiert und C-kompatible Skalartypen (`int32_t`, `double`, Rohzeiger) verwendet. |
| **Mehrfache `main`-Definition** | Definiere genau **ein** `main()` im gesamten Projekt (vorzugsweise in C++: `int main()`). In Go darf bei vorhandenem C++ `main()` kein `func main() {}` geschrieben werden. |
| **Android-Installation blockiert** | In den Android-Systemeinstellungen die Option *"Apps aus dieser Quelle erlauben"* für KR Compiler aktivieren. |
| **Linux-Programm startet nicht** | Führe im Terminal `chmod +x <dateiname>` aus. Es wird eine Standard-x86-64-Linux-Umgebung mit glibc benötigt. |

---

Stand: KR Compiler Android IDE v4.0.0, 15. Oktober 2026.
