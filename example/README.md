# Adaptive-Compute-Beispiel

Dieses vollständige KR-Unified-Projekt demonstriert einen unabhängigen,
elementweisen Vektor-Additionsloop.

## In der Android-App

1. Mit **Ordner öffnen** diesen `example`-Ordner auswählen.
2. Im Menü **Adaptive Compute analysieren…** wählen.
3. Die Analyse zeigt für `main.kr` die Operation `ELEMENTWISE_ADD` mit dem
   Ergebnis `GPU_ELIGIBLE` an.
4. Mit **Kompilieren** lässt sich dasselbe Projekt normal für die ausgewählte
   Zielplattform bauen.

Der Loop schreibt jedes Element von `out` genau einmal und liest nur die
gleich indizierten Elemente aus `a` und `b`. Es gibt innerhalb des Loops keine
unbekannten Funktionsaufrufe, Seiteneffekte oder schleifengetragenen
Abhängigkeiten.
