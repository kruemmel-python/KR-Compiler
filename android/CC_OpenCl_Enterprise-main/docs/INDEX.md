# CC_OpenCl Enterprise Agent-Ready Documentation Index

Stand: 2026-05-06

Diese Dokumentationsschicht wurde ergänzt, damit Entwickler, KI-Agenten und autonome Codegeneratoren den Treiber sicher verwenden können.

## Schnellstart für Menschen

1. `README.md` lesen.
2. `docs/CC_OpenCl_AgentReady_Enterprise_Documentation.pdf` öffnen.
3. Für Speicherplanung: `docs/VRAM_BUDGETING.md`.
4. Für Integration: `examples/python_ctypes_resonance.py` oder `examples/cpp_resonance_example.cpp`.

## Schnellstart für Agenten

1. `docs/DRIVER_MANIFEST.json` parsen.
2. `resource_policy.safe_vram_fraction` anwenden.
3. Domäne anhand `domains.*.purpose` wählen.
4. Buffer anhand `function_contracts` erzeugen.
5. Rückgabewerte prüfen und `cc_get_last_error()` auswerten.

## Dateien

| Datei | Inhalt |
|---|---|
| `CC_OpenCl_AgentReady_Enterprise_Documentation.pdf` | kompaktes Enterprise-Handbuch |
| `CC_OpenCl_AgentReady_Enterprise_Documentation.docx` | editierbare Handbuchversion |
| `CC_OPENCL_AGENT_READY_ENTERPRISE_MANUAL.md` | Markdown-Version des Handbuchs |
| `VRAM_BUDGETING.md` | spezifische VRAM-Tabellen pro Domäne |
| `AGENT_USAGE_GUIDE.md` | Agentenfähige API-Verträge |
| `AI_AGENT_CONTRACTS.md` | harte Regeln für autonome Systeme |
| `DRIVER_MANIFEST.json` | maschinenlesbares Manifest |
| `PIPELINE_RECIPES.md` | Integrationspipelines |
| `ERROR_HANDLING.md` | Fehlerreaktion und Recovery |
| `PARAMETER_SAFETY.md` | sichere Parameterprofile |
| `FUNCTION_SELECTION_MATRIX.md` | Funktionsauswahl und Exportliste |
