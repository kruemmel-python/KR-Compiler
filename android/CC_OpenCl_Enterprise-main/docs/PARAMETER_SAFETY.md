# Parameter Safety Profiles

Version: Enterprise Agent-Ready  
Stand: 2026-05-06

## Resonance Field

| Parameter | Sicherer Bereich | Gefahr bei Überschreitung | Agentenreaktion |
|---|---:|---|---|
| N | >= 10.000 | zu klein: GPU-Overhead dominiert | CPU oder kleinere Pipeline |
| K | 4, 8, 16 | hohe Bandbreite, schlechte Coalescing | K reduzieren |
| dt | 0.001 - 0.05 Standard | numerische Explosion | halbieren |
| damping | 0.01 - 0.20 | zu hoch: Feld stirbt | reduzieren |
| coupling | 0.05 - 0.50 | Oszillation/NaN | halbieren |
| inertia | 0.80 - 0.98 | Langzeitdrift | clamp setzen |
| clamp_abs | 1 - 100 | 0 erlaubt Explosion | >0 setzen |

## Energy Scheduler

| Parameter | Sicherer Bereich | Bedeutung |
|---|---:|---|
| threshold | p50 bis p90 der Energieverteilung | Ziel: 1-20 % aktiv |
| sleep_decay | 0.90 - 0.999 | langsame Dämpfung |
| nutrient_recovery | 0.0 - 0.01 | Hintergrundregeneration |

Agentenregel:

```pseudo
if active_rate > 0.8:
    threshold *= 1.25
if active_rate < 0.001:
    threshold *= 0.75
```

## Morphogenesis

| Parameter | Sicherer Bereich | Bedeutung |
|---|---:|---|
| R | 1 - 64 | Regeln pro Schritt |
| nutrient_cost | 0.0 - 1.0 | Transformationskosten |
| rule_min_nutrient | >= 0 | Ressourcenschwelle |
| rule_min_energy | >= 0 | Aktivitätsschwelle |

Regeln müssen versioniert werden. Eine geänderte Reihenfolge ist eine andere Simulation.

## Mycelium

| Parameter | Sicherer Bereich | Bedeutung |
|---|---:|---|
| C | 1 - 8 | Kanäle; erhöht VRAM linear |
| K | 4 - 16 | Nachbarschaft; erhöht VRAM und Rechenzeit |
| decay | 0.90 - 0.999 | Pheromonzerfall |
| diffusion | 0.01 - 0.25 | Ausbreitung |

## Quantum

| Parameter | Sicherer Bereich | Bedeutung |
|---|---:|---|
| q | durch VRAM begrenzt | Speicher wächst 2^q |
| iterations | Problemabhängig | Zeit wächst linear |
| ansatz_layers | klein starten | VQE/QAOA-Kosten |

VRAM für Statevector:

```text
state_bytes = 2^q * sizeof(float2)
scratch_bytes ≈ 2 * 2^q * sizeof(float)
```
