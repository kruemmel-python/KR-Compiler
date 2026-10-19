# Error Handling for Autonomous Systems

Version: Enterprise Agent-Ready  
Stand: 2026-05-06

Dieses Dokument definiert Fehlerreaktionen für Hostprogramme, KI-Agenten und Scheduler.

## Rückgabemodell

Viele exportierte Funktionen verwenden:

| Rückgabewert | Bedeutung |
|---:|---|
| 1 | Erfolg |
| 0 | Fehler; Details über `cc_get_last_error()` abrufen |

Einige Funktionen geben Pointer oder Messwerte zurück. Für Pointer gilt: `NULL` ist Fehler oder leere Ressource.

## Fehlerklassen

| Fehler/Symptom | Bedeutung | Sofortreaktion | Dauerhafte Lösung |
|---|---|---|---|
| CL_OUT_OF_RESOURCES | VRAM/Register/Workgroup erschöpft | laufende Pipeline stoppen | N/K/C reduzieren, Domänen sequenziell ausführen |
| CL_MEM_OBJECT_ALLOCATION_FAILURE | Buffer konnte nicht angelegt werden | Allokation abbrechen | Einzelbuffer prüfen, `max_mem_alloc_size` beachten |
| CL_INVALID_MEM_OBJECT | Bufferhandle ungültig | Buffer neu erzeugen | Ownership/Free-Reihenfolge korrigieren |
| CL_INVALID_KERNEL_ARGS | Argument fehlt/falsch | API-Vertrag prüfen | Funktionssignatur und Buffergrößen validieren |
| CL_BUILD_PROGRAM_FAILURE | Kernel konnte nicht gebaut werden | Buildlog sichern | OpenCL-Headers/Treiber/Build-Optionen prüfen |
| NaN/Inf im Output | numerische Instabilität | Clamp aktivieren | dt/coupling reduzieren, Eingaben normalisieren |
| active_count == N | Scheduler ohne Nutzen | Threshold erhöhen | Full-Step nutzen, wenn System global aktiv ist |
| active_count == 0 | System schläft | Drive erhöhen | Threshold senken oder Reiz injizieren |
| Wiederholter Fehler nach Release | Treiberzustand instabil | Prozess neu starten | Ressourcenmodell reduzieren |

## Agenten-Retry-Policy

```pseudo
max_attempts = 3

attempt 1:
    run requested config

attempt 2 after CL_OUT_OF_RESOURCES:
    N = N / 2
    K = min(K, 8)
    disable optional quantum or bio-brain domains

attempt 3:
    N = N / 4
    run only essential domain

if still failing:
    stop and report exact configuration and cc_get_last_error()
```

## Nicht erlaubte Agentenreaktionen

Ein Agent darf nicht:

- denselben fehlgeschlagenen Kernel unendlich wiederholen
- nach `CL_OUT_OF_RESOURCES` größere Buffer anlegen
- Buffer freigeben und alte Handles weiterverwenden
- `active_count_buf` ohne Reset wiederverwenden
- NaN-Ausgaben als gültige Simulation interpretieren
- Quantum-q erhöhen, ohne VRAM neu zu berechnen

## Diagnoseausgabe

Jede Produktionsintegration sollte bei Fehlern mindestens ausgeben:

```text
driver_version
gpu_index
selected_domain
N, K, C, R, q
estimated_required_bytes
safe_vram_bytes
function_name
return_code
cc_get_last_error()
```
