# CC OpenCL Enterprise Algorithm Pack

## Ziel

Der Enterprise Algorithm Pack erweitert `CC_OpenCL.c` um drei neue GPU-Algorithmen, die nicht als bloße Zusatzkernel, sondern als modulare Ausführungsschicht gedacht sind.

## Enthaltene Algorithmen

### 1. Resonant Field Propagation

Ein gekoppeltes Feldmodell für Mycelium-, SubQG-, Pheromon- und Agentensysteme.

Formel:

```text
velocity[i] = velocity[i] * inertia
            + coupling * neighbor_gradient
            + drive[i]
            - damping * state[i]

state[i] += dt * velocity[i]
energy[i] = abs(velocity[i]) + abs(neighbor_gradient)
```

Nutzen:

- ein Kernel für Diffusion, Dämpfung, Trägheit und Aktivitätsmessung
- keine Python-Schleifen
- Energie-Ausgabe kann als Scheduler-Signal genutzt werden

### 2. Energy-Gated Agent Scheduler

Ein GPU-interner Scheduler, der aktive Agenten anhand eines Energiefelds markiert und kompaktiert.

Erzeugt:

- `active_flags[N]`
- `active_indices[N]`
- `active_count[1]`

Der Ansatz verschiebt teure Agentenupdates von `O(N)` auf `O(N_scan + M_active_update)`, wobei der `N_scan` billig bleibt.

### 3. Morphogenetic Rule Kernel

Ein tabellarisches Zell-/Strukturwachstumsmodell.

Regeln werden als Arrays gespeichert:

```text
rule_in_type[R]
rule_min_nutrient[R]
rule_min_energy[R]
rule_out_type[R]
rule_delta_potential[R]
```

Jede Zelle prüft Regeln lokal und aktualisiert Typ, Potential und Nährstoff.

## Build

Im `x64 Native Tools Command Prompt for VS 2022`:

```cmd
cd /d D:\CC_OpenCl_Driver
scripts\build_windows_msvc_amd_direct.bat
```

## ABI

Die API bleibt C-kompatibel. OpenCL-Buffer werden als `void*` übergeben und intern zu `cl_mem` gecastet.

## Risiken

- Resonanzfelder können bei ungünstigen Parametern numerisch explodieren.
- Energy-Gating lohnt sich erst, wenn teure Folgekernels nur aktive Agenten verarbeiten.
- Morphogenese-Regeln sind absichtlich tabellarisch und weniger flexibel als Python-Regeln.
- Die drei neuen APIs erwarten bereits erzeugte OpenCL-Buffer mit korrekter Größe.
