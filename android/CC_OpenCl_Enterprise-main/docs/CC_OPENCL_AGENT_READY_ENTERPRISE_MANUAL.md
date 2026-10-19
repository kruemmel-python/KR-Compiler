# CC_OpenCl.dll Enterprise Agent-Ready Driver Manual

Version: Enterprise Agent-Ready  
Stand: 2026-05-06

## Zweck

`CC_OpenCl.dll` ist ein Windows-x64-OpenCL-Treiber für GPU-beschleunigte Feld-, Agenten-, Mycelium-, SubQG-, Bio-Brain-, Quantum- und Enterprise-Algorithmus-Domänen. Diese Dokumentation ist so erweitert, dass sowohl Entwickler als auch KI-Agenten den Treiber sicher für Programmierungen einsetzen können.

## Architekturüberblick

Der Treiber stellt eine C-kompatible DLL-Schnittstelle bereit. Hostprogramme initialisieren eine GPU, erzeugen OpenCL-Buffer, übertragen Daten, starten Domänenfunktionen und lesen Ergebnisse zurück. Große Simulationszustände sollen möglichst im VRAM bleiben, damit Host-Device-Transfers nicht dominieren.

### Domänen

| Domäne | Technisches Modell | Typische Funktionen |
|---|---|---|
| Lifecycle | GPU-Kontext, Queue, Programme | `initialize_gpu`, `shutdown_gpu`, `finish_gpu` |
| Memory | OpenCL-Buffer und Transfers | `allocate_gpu_memory`, `write_host_to_gpu_blocking`, `read_gpu_to_host_blocking` |
| Mycelium | Nährstoff-, Pheromon-, Mood- und Koloniefelder | `subqg_init_mycel`, `step_pheromone_diffuse_decay`, `step_mycel_update` |
| SubQG | acht gekoppelte Multifield-Kanäle | `subqg_set_multifield_state`, `subqg_get_multifield_state` |
| Resonance | gekoppelte Feldoszillatoren | `execute_resonant_field_step_gpu` |
| Scheduler | GPU-seitige aktive Liste | `execute_energy_gated_scheduler_gpu` |
| Morphogenesis | tabellarische Zelltyp-Regeln | `execute_morphogenetic_rule_step_gpu` |
| Bio-Brain | neuronale/agentische Dynamik | Bio-Brain und Lernfunktionen |
| Quantum | quantenähnliche Algorithmen | `execute_grover_gpu`, `execute_vqe_gpu`, `execute_qaoa_gpu` |
| Cognitive | Signalresonanz und narrative Vektoren | `compute_qualia_resonance_gpu`, `generate_dream_state_gpu` |

## Build und Einsatz

Empfohlener Build auf AMD/Windows:

```cmd
cd /d D:\CC_OpenCl_Driver_Enterprise
scripts\build_windows_msvc_amd_direct.bat
```

Voraussetzungen:

- x64 Native Tools Command Prompt for VS 2022
- AMD/OpenCL Runtime über GPU-Treiber
- `CL\OpenCL.def`
- Khronos OpenCL-Headers im `CL`-Ordner

Build-Ergebnis:

```text
build\CC_OpenCl.dll
build\CC_OpenCl.lib
build\CC_OpenCl.exp
```

## Sichere Grundsequenz

```pseudo
initialize_gpu(gpu)
allocate required buffers
write initial data
execute domain functions
read result if needed
free buffers
shutdown_gpu(gpu)
```

Ein Agent darf keine Domänenfunktion vor `initialize_gpu` aufrufen und darf keine GPU-Handles nach `free_gpu_memory` wiederverwenden.

## VRAM-Budgeting

Enterprise-Default:

```text
safe_vram_bytes = CL_DEVICE_GLOBAL_MEM_SIZE * 0.65
planned_bytes = required_bytes * 1.15
planned_bytes <= safe_vram_bytes
```

### Standardverbrauch bei 1024 x 1024, K=8, C=4

| Domäne | Formel | Standardverbrauch |
|---|---|---|
| Mycelium Core | pheromone[N*C*K]*float + mood[N*C]*float + nutrient[N]*float + potential[N]*float + colonies[N]*uint8 + neighbors[N*K]*int | 185.00 MiB |
| SubQG Multifield | 8 scalar channels[N]*float | 32.00 MiB |
| Resonance Field | state/velocity/drive/energy[N]*float + neighbors[N*K]*int + weights[N*K]*float | 80.00 MiB |
| Energy Scheduler | active_flags[N]*uchar + active_indices[N]*int + active_count[1]*uint | 5.00 MiB |
| Morphogenesis | cell_type[N]*uchar + nutrient/energy/potential[N]*float + rule tables[R] | 13.00 MiB |
| Bio-Brain Standard | neurons=N, fanout=K, synapses=N*K: neuron scalar buffers + sparse synapse tables | 149.00 MiB |
| Quantum Statevector | complex amplitudes[2^q]*float2 + probability/phase scratch | 256.00 MiB |

### Skalierung

| Grid | N | Mycel MiB | SubQG MiB | Resonance MiB | Scheduler MiB | Morph MiB | Bio-Brain MiB |
|---|---|---|---|---|---|---|---|
| 256x256 | 65,536 | 11.6 | 2.0 | 5.0 | 0.3 | 0.8 | 9.3 |
| 512x512 | 262,144 | 46.2 | 8.0 | 20.0 | 1.3 | 3.3 | 37.2 |
| 1024x1024 | 1,048,576 | 185.0 | 32.0 | 80.0 | 5.0 | 13.0 | 149.0 |
| 2048x2048 | 4,194,304 | 740.0 | 128.0 | 320.0 | 20.0 | 52.0 | 596.0 |
| 4096x4096 | 16,777,216 | 2960.0 | 512.0 | 1280.0 | 80.0 | 208.0 | 2384.0 |

## API-Verträge der neuen Enterprise-Algorithmen

### execute_resonant_field_step_gpu

Zweck: Ein gekoppelte Feldschritt mit Nachbarschaftsgradient, Trägheit, Drive, Dämpfung und Energieausgabe.

Buffer:

| Buffer | Typ | Größe | Mutiert |
|---|---|---:|---|
| state_buf | float | N | ja |
| velocity_buf | float | N | ja |
| drive_buf | float | N | nein |
| energy_buf | float | N | ja |
| neighbors_buf | int | N*K | nein |
| weights_buf | float | N*K | nein |

Sichere Parameter:

| Parameter | Bereich |
|---|---:|
| K | 4, 8, 16 |
| dt | 0.001 - 0.05 |
| damping | 0.01 - 0.20 |
| coupling | 0.05 - 0.50 |
| inertia | 0.80 - 0.98 |
| clamp_abs | >0 empfohlen |

### execute_energy_gated_scheduler_gpu

Zweck: Extrahiert aktive Zellen/Agenten über ein Energiefeld und schreibt `active_flags`, `active_indices`, `active_count`.

Wichtig: `active_count_buf` muss vor jedem Aufruf auf 0 gesetzt werden.

### execute_morphogenetic_rule_step_gpu

Zweck: Transformiert `cell_type[N]` anhand tabellarischer Regeln und Feldzustände.

Regel: Die erste passende Regel gewinnt. Regelreihenfolge ist Teil der Simulation und muss versioniert werden.

## Agentenentscheidungen

Ein KI-Agent soll Funktionen über Ziel und Domäne auswählen:

| Ziel | Funktion |
|---|---|
| gekoppelte Feldaktivität | `execute_resonant_field_step_gpu` |
| aktive Teilmenge finden | `execute_energy_gated_scheduler_gpu` |
| Zelltypregeln anwenden | `execute_morphogenetic_rule_step_gpu` |
| Mycelium/Pheromone | `step_pheromone_*`, `step_mycel_update` |
| Multifeld lesen/schreiben | `subqg_set_multifield_state`, `subqg_get_multifield_state` |
| Quantum-Optimierung | Quantum-Funktionsgruppe |
| GPU-Speicher | Memory-Funktionsgruppe |

## Fehlerbehandlung

| Fehler | Ursache | Agentenreaktion |
|---|---|---|
| CL_OUT_OF_RESOURCES | VRAM/Register/Workgroup erschöpft | N halbieren, K/C reduzieren, Domänen sequenziell |
| CL_MEM_OBJECT_ALLOCATION_FAILURE | Buffer zu groß | Einzelbuffer prüfen |
| CL_INVALID_MEM_OBJECT | falsches/gelöschtes Handle | Buffer neu erzeugen |
| CL_INVALID_KERNEL_ARGS | Vertragsbruch | Signatur und Buffergrößen prüfen |
| NaN/Inf | numerische Instabilität | clamp setzen, dt/coupling reduzieren |
| active_count == N | Scheduler bringt keinen Vorteil | Threshold erhöhen oder Full-Step |
| active_count == 0 | keine Aktivität | Drive erhöhen oder Threshold senken |

## Pipeline-Rezept: Resonance + Scheduler

```pseudo
initialize_gpu
allocate state, velocity, drive, energy, neighbors, weights
allocate active_flags, active_indices, active_count

loop:
    execute_resonant_field_step_gpu
    write active_count = 0
    execute_energy_gated_scheduler_gpu
    read active_count
    adapt threshold or process active indices
```

## Maschinenlesbare Dokumentation

`docs/DRIVER_MANIFEST.json` enthält:

- Treibername, Plattform, Buildscript
- Domänen, Zwecke, VRAM-Formeln
- Bufferverträge
- sichere Parameterbereiche
- Exportfunktionsliste
- Fehler- und Ressourcenpolitik

Agentensysteme sollten zuerst dieses Manifest lesen und danach `docs/AGENT_USAGE_GUIDE.md` und `docs/VRAM_BUDGETING.md` verwenden.

## Harte Regeln für KI-Agenten

1. Vor jeder Allokation VRAM schätzen.
2. `planned_bytes <= global_mem * 0.65`.
3. `active_count_buf` vor Schedulerlauf auf 0 setzen.
4. Keine alten Handles nach Free verwenden.
5. Keine NaN/Inf-Ergebnisse akzeptieren.
6. Bei `CL_OUT_OF_RESOURCES` Problemgröße reduzieren.
7. Quantum-q nur nach neuer Speicherprüfung erhöhen.
8. Regelreihenfolgen dokumentieren und versionieren.

## Dokumentationsdateien

| Datei | Zweck |
|---|---|
| `docs/VRAM_BUDGETING.md` | Speicherformeln und Tabellen |
| `docs/AGENT_USAGE_GUIDE.md` | KI-/Agenten-Nutzung |
| `docs/AI_AGENT_CONTRACTS.md` | harte API-Verträge |
| `docs/DRIVER_MANIFEST.json` | maschinenlesbarer Vertrag |
| `docs/PIPELINE_RECIPES.md` | Ablaufmuster |
| `docs/ERROR_HANDLING.md` | Recovery-Strategien |
| `docs/PARAMETER_SAFETY.md` | Parameterprofile |
| `examples/` | Referenzintegration |
