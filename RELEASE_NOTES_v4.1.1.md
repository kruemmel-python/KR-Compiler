# KR Compiler IDE v4.1.1

Version 4.1.1 behebt die falsche Meldung, nach der Adaptive Compute in der
Android-App nur mit einem „lokalen“ Projektordner verwendet werden konnte.

## Fehlerbehebung

- Über Androids System-Ordnerauswahl geöffnete Projekte werden nun unterstützt.
- Die App liest den Storage-Access-Framework-Ordner im Hintergrund in einen
  temporären app-internen Analysebereich ein.
- Die aktive geänderte Datei wird vor der Analyse gespeichert.
- Der Analysebereich wird nach Abschluss wieder entfernt.
- Der Dialog blockiert die Benutzeroberfläche während des Einlesens nicht.

## Beispielprojekt

Das Repository und der Release enthalten ein `example`-Projekt mit einer
elementweisen Vektoraddition. Erwartetes Analyseergebnis:

- Operation: `ELEMENTWISE_ADD`
- Einstufung: `GPU_ELIGIBLE`
- Lesen: `a`, `b`
- Schreiben: `out`

## Downloads

- `KR-Compiler-Android-IDE-v4.1.1.apk`
- `KR-Compiler-Windows-x64-v4.1.1.zip`
- `KR-Compiler-Linux-x64-v4.1.1.tar.xz`
- `KR-Compiler-Windows-toolchain-v4.1.1.zip`
- `KR-Compiler-Adaptive-Compute-example-v4.1.1.zip`
- `SHA256SUMS-v4.1.1.txt`

## Verifikation

- Sauberer Android-Test- und Release-Build: 146 Aufgaben erfolgreich.
- Beispiel nativ kompiliert und ausgeführt: acht Ergebnisse mit dem Wert `5`.
- Analyzer-Test bestätigt `ELEMENTWISE_ADD / GPU_ELIGIBLE`.
- Windows- und Linux-Portable-Pakete erneut vollständig geprüft.

Der umfassende Enterprise-Auftrag bleibt aus den in
`docs/IMPLEMENTATION_STATUS.md` dokumentierten Gründen weiterhin
**INCOMPLETE**.
