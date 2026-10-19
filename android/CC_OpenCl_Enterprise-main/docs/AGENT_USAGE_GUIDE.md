# Agent Usage Guide

Version: Enterprise Agent-Ready  
Stand: 2026-05-06

Dieses Dokument ist für KI-Agenten, Codegeneratoren und autonome Hostsysteme geschrieben. Es beschreibt nicht nur, welche Funktionen existieren, sondern wann sie sicher eingesetzt werden dürfen, welche Bufferverträge gelten und welche Entscheidungen vor einem Aufruf getroffen werden müssen.

## Grundprinzip

`CC_OpenCl.dll` ist eine zustandsbehaftete OpenCL-Runtime. Ein Agent muss immer in dieser Reihenfolge denken:

```text
1. Ziel bestimmen
2. Domäne wählen
3. VRAM schätzen
4. GPU initialisieren
5. Buffer anlegen
6. Daten hochladen
7. Kernel/API aufrufen
8. Ergebnis lesen oder auf GPU belassen
9. Fehler abfragen
10. Ressourcen freigeben
```

## Minimale sichere Aufrufsequenz

```pseudo
gpu = 0

assert initialize_gpu(gpu) == 1

buffer = allocate_gpu_memory(gpu, bytes)
assert buffer != NULL

assert write_host_to_gpu_blocking(gpu, buffer, 0, bytes, host_ptr) == 1

status = domain_function(...)
if status != 1:
    error = cc_get_last_error()
    handle_error(error)

assert read_gpu_to_host_blocking(gpu, buffer, 0, bytes, out_ptr) == 1

free_gpu_memory(gpu, buffer)
shutdown_gpu(gpu)
```

## Funktionsauswahlmatrix

| Ziel | Empfohlene Domäne | Funktion/Gruppe | Agentenhinweis |
|---|---|---|---|
| GPU starten/beenden | Lifecycle | `initialize_gpu`, `shutdown_gpu`, `finish_gpu` | Immer vor/nach Domänenfunktionen nutzen |
| Speicher verwalten | Memory | `allocate_gpu_memory`, `free_gpu_memory`, `write_host_to_gpu_blocking`, `read_gpu_to_host_blocking` | Buffergrößen vorab über VRAM-Modell prüfen |
| Pheromone/Nährstoff/Wachstum | Mycelium | `subqg_init_mycel`, `step_pheromone_*`, `step_mycel_update` | K, C und N dominieren VRAM |
| Multifeld-Zustände | SubQG | `subqg_set_multifield_state`, `subqg_get_multifield_state` | Acht Kanäle float[N] |
| Resonante Felder | Enterprise Algorithms | `execute_resonant_field_step_gpu` | Default für gekoppelte Feldaktivität |
| Aktive Agenten filtern | Enterprise Algorithms | `execute_energy_gated_scheduler_gpu` | Nutzt Energie als Attention-/Aktivitätsfeld |
| Zelltypen transformieren | Enterprise Algorithms | `execute_morphogenetic_rule_step_gpu` | Regeln sind tabellarisch und prioritätsabhängig |
| Quantenähnliche Algorithmen | Quantum | `execute_grover_gpu`, `execute_vqe_gpu`, `execute_qaoa_gpu` usw. | Statevector-Skalierung beachten |
| Kognitive Vektorsynthese | Cognitive | `compute_qualia_*`, `generate_dream_state_gpu`, `plan_transformation_gpu` | Signalvektoren müssen gleich lang sein |
| SQSE/Rauschen | SQSE/Noise | `execute_sqse_*`, `set_noise_level` | Für Verschlüsselungs-/Maskierungs- und Noise-Control |
| Simulierter Kernel-Speicher | Simulated VRAM | `simulated_kernel_*` | Nur verwenden, wenn virtuelle Adressierung explizit gewünscht ist |

## Agentenfähige Funktionsverträge

### execute_resonant_field_step_gpu

**Zweck:** Führt einen gekoppelten Feldschritt mit Trägheit, Dämpfung, Nachbarschaftskopplung, Drive und Energieausgabe aus.

**Geeignet für:** Mycel-Spannungsfelder, Agentenaktivierung, Pheromonsteuerung, diffuse Reizleitung, Graphfelder, Scheduler-Energie.

**Nicht geeignet für:** kleine N < 10.000, extrem zufällige Nachbarschaften mit K > 32, instabile Parameter ohne Clamp.

| Parameter | Vertrag | Seiteneffekt |
|---|---|---|
| `state_buf` | `float[N]`, gültiger OpenCL-Buffer | wird überschrieben |
| `velocity_buf` | `float[N]` | wird überschrieben |
| `drive_buf` | `float[N]` | read-only |
| `energy_buf` | `float[N]` | wird überschrieben |
| `neighbors_buf` | `int[N*K]` | read-only |
| `weights_buf` | `float[N*K]` | read-only |
| `N` | > 0 | global size |
| `K` | 4, 8, 16 empfohlen | Nachbarschaftsgrad |
| `dt` | 0.001 bis 1.0 | Zeitschritt |
| `damping` | 0.0 bis 1.0 | Stabilisierung |
| `coupling` | 0.0 bis 2.0 | Kopplungsstärke |
| `inertia` | 0.0 bis 1.0 | Gedächtnis/Trägheit |
| `clamp_abs` | 0 deaktiviert, >0 empfohlen | NaN-Prävention |

**Agentenregel:** Wenn nach einem Schritt NaN/Inf entsteht, setze `clamp_abs=10.0`, halbieren `coupling` und `dt`, dann wiederholen.

### execute_energy_gated_scheduler_gpu

**Zweck:** Markiert aktive Agenten/Zellen anhand eines Energiefeldes und führt leichten Schlaf-/Decay-Step aus.

| Parameter | Vertrag | Seiteneffekt |
|---|---|---|
| `energy_buf` | `float[N]` | kann durch Decay geändert werden |
| `nutrient_buf` | `float[N]` | kann Recovery erhalten |
| `active_flags_buf` | `uchar[N]` | wird überschrieben |
| `active_indices_buf` | `int[N]` | aktive Indizes |
| `active_count_buf` | `uint[1]` | muss vor Aufruf auf 0 gesetzt werden |
| `threshold` | Energiegrenze | zu niedrig => alle aktiv |
| `sleep_decay` | 0.0 bis 1.0 | Schlafdämpfung |
| `nutrient_recovery` | >= 0 | Recovery inaktiver Zellen |

**Agentenregel:** Wenn `active_count == N`, bringt der Scheduler keinen Vorteil. Erhöhe `threshold` oder verwende Full-Step. Wenn `active_count == 0`, senke `threshold` oder erhöhe Drive.

### execute_morphogenetic_rule_step_gpu

**Zweck:** Transformiert Zelltypen anhand tabellarischer Regeln und Feldzustände.

| Parameter | Vertrag | Seiteneffekt |
|---|---|---|
| `cell_type_buf` | `uchar[N]` | Zelltyp wird geändert |
| `nutrient_buf` | `float[N]` | Kosten werden abgezogen |
| `energy_buf` | `float[N]` | read-only |
| `potential_buf` | `float[N]` | wird angepasst |
| `rule_in_type_buf` | `int[R]` | read-only |
| `rule_min_nutrient_buf` | `float[R]` | read-only |
| `rule_min_energy_buf` | `float[R]` | read-only |
| `rule_out_type_buf` | `int[R]` | read-only |
| `rule_delta_potential_buf` | `float[R]` | read-only |
| `N` | > 0 | Zellen |
| `R` | 1 bis 64 empfohlen | Regeln |
| `nutrient_cost` | >= 0 | Kosten pro Transformation |

**Agentenregel:** Regeln sind priorisiert. Die erste passende Regel gewinnt. Ein Agent muss die Regelreihenfolge dokumentieren.

## Sichere Parameterprofile

| Profil | N | K | dt | damping | coupling | inertia | clamp_abs | Einsatz |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| Safe Small | 65.536 | 4 | 0.05 | 0.05 | 0.25 | 0.90 | 10.0 | Tests/CI |
| Standard Enterprise | 1.048.576 | 8 | 0.03 | 0.03 | 0.20 | 0.92 | 10.0 | Produktion |
| High VRAM | 4.194.304 | 8 | 0.02 | 0.02 | 0.15 | 0.95 | 8.0 | Dedizierte GPU |
| Experimental | frei | 16+ | <=0.02 | >=0.02 | >1.0 | 0.8-0.99 | zwingend | Forschung |

## Pflichtentscheidungen für Agenten

Vor dem Schreiben von Code muss ein Agent diese Fragen beantworten:

1. Ist die Aufgabe feldbasiert, agentenbasiert, quantenähnlich oder morphogenetisch?
2. Bleiben Daten über mehrere Schritte im VRAM?
3. Ist `N` groß genug, damit GPU-Dispatch lohnt?
4. Überschreitet die Allokation `0.65 * CL_DEVICE_GLOBAL_MEM_SIZE`?
5. Muss ein Counter vor dem Aufruf auf 0 gesetzt werden?
6. Wird ein Buffer in-place überschrieben?
7. Welche Rückfallstrategie gilt bei `CL_OUT_OF_RESOURCES`?
8. Welche Parameter können numerisch explodieren?
