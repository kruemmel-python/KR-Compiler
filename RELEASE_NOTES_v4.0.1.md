# KR Compiler Android IDE v4.0.1 – Release Notes

Version 4.0.1 baut auf dem Meilenstein der **KR Unified Language (`.kr`)** auf und erweitert die mobile IDE um vollständige Unterstützung für anspruchsvolle Enterprise-GPU-Bibliotheken, OpenCL-Entwicklung und gehärtete Windows-x64-Cross-Kompilierung.

---

## Neuerungen in Version 4.0.1

### 1. Enterprise C++ OpenCL & GPU-Bibliotheken für Windows x64
- **Erfolgreiche Verifikation anhand `CC_OpenCl_Enterprise`**: Vollständige mobile Kompilierung der 140 Export-Funktionen umfassenden GPU-Bibliothek direkt auf einem Android-Smartphone für Windows x64.
- **LLVM-MinGW Toolchain-Härtung**:
  - Konsequente Nutzung der integrierten LLVM-Laufzeit (`libc++`, `compiler-rt`, `libunwind`) auch bei Shared Libraries (`.dll`), wodurch veraltete GCC-Laufzeitabhängigkeiten (`-lgcc`, `-lgcc_s`) vollständig eliminiert wurden.
  - Sichere PE/COFF-Erzeugung mit LLD (`lld-link`) ohne externe Host-SDK-Abhängigkeiten.
- **VCPKG- & OpenCL-Architektur**:
  - Nahtlose Koexistenz von mobilem Offline-VCPKG-Katalog und Repository-eigenen SDK-Headern/Importbibliotheken.
  - Automatischer Khronos OpenCL 3.0 Fallback bei plattformübergreifenden GPU-Projekten.

### 2. End-to-End Verifikation & Hardware-GPU-Ausführung
- **100,0 % API-Parität**: Alle 140 GPU-, Tensor-, Spiking-Neuron- und Simulationsfunktionen wurden im Binärvergleich 1:1 nachgewiesen.
- **Hardware-GPU-Testlauf (AMD gfx90c)**:
  - Kompilierung von über 60 OpenCL-Kerneln.
  - VRAM-Pufferallokation, Host-to-Device-Transfer und Vektoraddition (`execute_add_on_gpu`).
  - Exakter numerischer Abgleich gegen CPU-Referenzwerte.

### 3. KR Unified Language (.kr)
- Weiterhin vollständige Unterstützung der nahtlosen Sprachfusion von **C++20, Rust und Go** innerhalb einer einzigen `.kr`-Datei ohne Sprachpräfixe.
- Alle 18 Kombinationen (6 Vorlagen × 3 Zielplattformen Android ARM64, Windows x64, Linux x64) bleiben uneingeschränkt verifiziert.

---

## Prüfsumme

- **Datei**: `KR-Compiler-Android-IDE-v4.0.1.apk`
- **Größe**: `743.542.256 Bytes`
- **SHA-256**: `7AD6D645596E53F8CC315B7752CCFCE18D3B618C64A31A9981650424FBF049FF`
- **Signatur**: Release-Entwicklungsschlüssel (v4.0-kompatibel)
