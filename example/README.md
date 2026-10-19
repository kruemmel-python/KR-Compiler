# Adaptive-Compute-Beispiel

Dieses vollständige KR-Unified-Projekt demonstriert einen unabhängigen,
elementweisen Vektor-Additionsloop und die generierte portable Laufzeit-API.

## In der Android-App

1. Mit **Ordner öffnen** diesen `example`-Ordner auswählen.
2. Im Menü **Adaptive Compute analysieren…** wählen.
3. Die Analyse zeigt für `main.kr` die Operation `ELEMENTWISE_ADD` mit dem
   Ergebnis `GPU_ELIGIBLE` an.
4. Mit **Kompilieren** lässt sich dasselbe Projekt normal für die ausgewählte
   Zielplattform bauen.
5. Beim Start entscheidet `kr_compute_add_f32` im Projektmodus `AUTO` zwischen
   CPU und GPU. Die Datei `adaptive_compute_telemetry.jsonl` enthält danach
   Entscheidung, Zeiten, Transfermengen, Gerätespeicher, Fallback und Validierung.

Der Loop schreibt jedes Element von `out` genau einmal und liest nur die
gleich indizierten Elemente aus `a` und `b`. Es gibt innerhalb des Loops keine
unbekannten Funktionsaufrufe, Seiteneffekte oder schleifengetragenen
Abhängigkeiten.

Für numerisch sensible Prüfungen kann in der Projektdatei `mode = "VERIFY"`
gesetzt werden. Dann wird ein GPU-Ergebnis mit der CPU-Referenz verglichen.
