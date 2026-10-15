# KR Compiler Android IDE v4.0 Testprotokoll

Dieses Testprotokoll dokumentiert die vollständige Verifikation von **KR Compiler Android IDE v4.0.0** auf dem physischen Testgerät **Realme GT 6T (RMX3853, Android 15, ARM64-v8a)** sowie auf **Windows x64** und **Linux x64 (Ubuntu / WSL)**.

---

## 1. Testmatrix: 18 von 18 Builds erfolgreich

Es wurden sechs repräsentative Projekttypen für alle drei Zielsysteme erzeugt und verifiziert:

| Projekttyp | Quellsprachen | Android APK (ARM64) | Windows EXE (x64) | Linux ELF (x64) | Status |
|---|---|---:|---:|---:|:---:|
| **KR Konsole** | `.kr` (C++20) | 676.733 Byte | 1.448.960 Byte | 1.424.912 Byte | **PASS** |
| **KR GUI (SDL3)** | `.kr` (C++20, SDL3) | 1.479.624 Byte | 4.166.656 Byte | 4.548.296 Byte | **PASS** |
| **Rust Konsole** | Cargo, `.rs` | 824.189 Byte | 549.888 Byte | 386.496 Byte | **PASS** |
| **Go Konsole** | `go.mod`, `.go` | 1.987.453 Byte | 3.379.200 Byte | 3.312.768 Byte | **PASS** |
| **KR Sprachfusion Konsole** | `.kr` (C++20 + Rust + Go) | 1.995.733 Byte | 4.815.360 Byte | 4.642.456 Byte | **PASS** |
| **KR Sprachfusion GUI** | `.kr` (C++20 + Rust + Go + SDL3) | 2.794.528 Byte | 7.546.880 Byte | 7.879.448 Byte | **PASS** |

---

## 2. DSP-Stresstest: KR_FUSION_RoPE_Attention

Als realer mathematischer und sprachübergreifender Härtetest wurde das Projekt `KR_FUSION_RoPE_Attention` in einer einzigen `.kr`-Datei entwickelt und kompiliert:

- **Architektur des Algorithmus**:
  - **C++20**: Tensorkern, Speicherlayout, Cache-Blocked Matrixmultiplikation ($Q \times K^T$), Online-Softmax und Akkumulation mit $V$.
  - **Rust**: Numerisch stabile RoPE (Rotary Position Embedding) Frequenz-Berechnung mit sin/cos-Tensortransformation.
  - **Go**: Mathematische Validierung, L2-Fehlernorm gegen analytische Referenz und Endabnahme.
- **Ergebnis**:
  - Kompiliert zu Android-APK (`KR_FUSION_RoPE_Attention.apk`, **2.003.925 Byte**).
  - Auf physischem Gerät RMX3853 installiert und über Text-Runner ausgeführt.
  - **Selbsttest-Ergebnis**:
    ```text
    [KR_FUSION_RoPE_Attention] Starte hybriden LLM-Attention-Kern...
    [Rust] Berechne Rotary Positional Embeddings fuer d_head=64, seq_len=128...
    [C++] Berechne geblockte Online-Softmax Attention...
    [Go] Validiere Attention-Tensoren und berechne L2-Fehlernorm...
    SELFTEST PASS max_abs_error=1.6653345369377348e-16
    RESULT=-4325.8755857397482
    Exit Code: 0
    ```
  - Bewiesene numerische Präzision an der Grenze von 64-Bit IEEE 754 Float ($\approx 10^{-16}$).

---

## 3. Laufzeit- und Plattformverifikation

### Android (ARM64-v8a)
- Alle generierten APKs wurden mit dem internen Signaturschlüssel signiert und via `adb install -r` auf dem RMX3853 installiert.
- Konsolenprogramme starteten zuverlässig in `kr.compile.runner`, lieferten stdout/stderr an das Log und beendeten sich mit Returncode 0.
- GUI-Programme starteten in der nativer `SDLActivity` und zeigten eine hardwarebeschleunigte Grafikausgabe (OpenGL ES / Vulkan).

### Windows (x86_64)
- Alle generierten `.exe`-Binärdateien wurden auf Windows 11 x64 nativ ausgeführt.
- Konsolen-Apps führten die FFI-Kreuzaufrufe (C++ $\rightarrow$ Rust $\rightarrow$ Go $\rightarrow$ C++) ohne Abstürze oder Heap-Beschädigung aus.
- GUI-Apps öffneten ein 60-FPS SDL3-Fenster.

### Linux (x86_64 ELF)
- Alle ELF-Dateien wurden unter Ubuntu x64 (glibc 2.35+) ausgeführt.
- Überprüfung der ELF-Header mit `file` und `readelf`: Valide 64-Bit LSB shared objects / executables, korrekte dynamische Verlinkung zu `libc.so.6`, `libm.so.6` und `libpthread.so.0`.

---

## 4. Release APK Prüfsummen

- **Datei**: `KR-Compiler-Android-IDE-v4.0.0.apk`
- **Größe**: ca. 1.050.000 Byte (optimiertes Release-Paket)
- **SHA-256**: `EA445F39EA7DF69CA1BB1050A1AC93CA5A4F6A7D4E9A01F1CC225FE93791C7B9`
- **Signatur**: Release-Entwicklerschlüssel (`CN=KR Compiler, OU=IDE, O=KR, C=DE`)
