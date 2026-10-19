# Adaptive Compute – reproduzierbare Validierung

Die Werkzeuge schreiben ausschließlich in den angegebenen Projekt-/Ausgabeordner. Sie melden fehlende Betriebssysteme, GPUs oder physische Geräte als `SKIPPED` und niemals als bestanden.

- `benchmark_runner.py TELEMETRY.jsonl --output REPORT.json` trennt den ersten Kaltlauf von Warmläufen und berechnet robuste Zeitstatistiken.
- `regression_matrix.py --output D:/android_project/build/adaptive-compute/matrix.json` führt nur lokal mögliche Matrixzeilen aus.
- `android_physical_e2e.ps1 -Apk ...` erkennt und dokumentiert ein echtes Android-Gerät. Erst eine anschließend erzeugte VERIFY-Telemetrie darf als GPU-Ergebnisvalidierung gelten.

Energie bleibt `NOT_MEASURED`, solange keine reale Messquelle `energyJoules` liefert. Aus Laufzeit oder Akkuprozent werden keine Joule-Werte geschätzt.
