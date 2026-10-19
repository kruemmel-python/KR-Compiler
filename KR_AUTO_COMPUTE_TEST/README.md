# KR_AUTO_COMPUTE_TEST

Dieses Projekt enthält normale FP32-Schleifen für Vektoraddition und
Vektormultiplikation. Es verwendet weder `kr_compute_*` noch OpenCL direkt.
Der KR-Compiler erkennt und ersetzt die beiden sicheren Schleifen automatisch.

Projektordner in der KR Compiler IDE öffnen, Zielplattform auswählen und
**Kompilieren** starten. Der Modus wird in `KR_AUTO_COMPUTE_TEST.krproject`
unter `[compute]` eingestellt: `AUTO`, `CPU_ONLY`, `GPU_ONLY` oder `VERIFY`.

Bei erfolgreichem Start erscheint:

```text
KR AUTO COMPUTE OK: 1027 / 3072
```

`AUTO` darf bei einer nicht verfügbaren GPU auf die CPU-Referenz zurückfallen.
`GPU_ONLY` beendet das Programm dagegen mit einem klaren Fehler, falls die
vorgesehene GPU-Operation nicht ausgeführt werden kann.
