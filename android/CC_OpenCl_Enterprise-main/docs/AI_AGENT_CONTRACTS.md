# AI Agent Contracts

Version: Enterprise Agent-Ready  
Stand: 2026-05-06

Diese Datei ist ein bindender Vertrag für KI-Agenten, die `CC_OpenCl.dll` verwenden.

## Harte Regeln

1. Keine Domänenfunktion vor `initialize_gpu`.
2. Keine Buffer ohne vorherige VRAM-Schätzung.
3. Kein Wiederverwenden von Handles nach `free_gpu_memory`.
4. `active_count_buf` vor jedem Schedulerlauf auf 0 setzen.
5. Keine Quantum-Statevector-Erhöhung ohne exponentielle Speicherprüfung.
6. Kein Ignorieren von `cc_get_last_error` nach Rückgabewert 0.
7. Keine Interpretation von NaN/Inf als gültiger Zustand.
8. Keine parallele Initialisierung derselben GPU ohne externe Synchronisation.

## Speicher-Ownership

| Aktion | Eigentümer | Regel |
|---|---|---|
| `allocate_gpu_memory` | Treiber/GPU | Host besitzt nur Handle |
| `free_gpu_memory` | Host ruft frei | Nach Free ist Handle ungültig |
| `write_host_to_gpu_blocking` | Hostdaten bleiben Host | Treiber kopiert |
| `read_gpu_to_host_blocking` | Hostziel muss groß genug sein | Keine Größenprüfung im Hostpointer |
| Domain-internal init | Treiber | mit `shutdown_gpu`/Release freigeben |

## Entscheidungsbaum

```text
Aufgabe: Feld aktualisieren?
    Ja -> Gibt es Nachbarschaften?
        Ja -> Resonance Field oder Mycelium
        Nein -> einfacher Vektor-/Tensor-Kernel
Aufgabe: aktive Teilmenge finden?
    Ja -> Energy Scheduler
Aufgabe: Zelltypen ändern?
    Ja -> Morphogenesis
Aufgabe: neuronale Dynamik?
    Ja -> Bio-Brain
Aufgabe: Quantenähnliche Optimierung?
    Ja -> Quantum, VRAM isoliert planen
```

## Generierungsregeln für Codegeneratoren

Ein Codegenerator muss:

- Prototypen aus `include/SymBio_Interface.h` oder eigenem FFI exakt übernehmen.
- Pointer als `void*`/`ctypes.c_void_p` behandeln, nicht als Hostarrays.
- Größen als `size_t` übertragen, wo die API es verlangt.
- Vor jedem Funktionsaufruf prüfen, ob die Bufferformel erfüllt ist.
- Nach jedem Rückgabewert `0` `cc_get_last_error()` abrufen.
- Bei Produktionscode einen Cleanup-Pfad mit `try/finally`, RAII oder äquivalentem Muster erzeugen.

## FFI-Mapping für Python ctypes

| C-Typ | ctypes |
|---|---|
| `int` | `ctypes.c_int` |
| `float` | `ctypes.c_float` |
| `size_t` | `ctypes.c_size_t` |
| `void*` | `ctypes.c_void_p` |
| `const char*` | `ctypes.c_char_p` |
| `uint64_t` | `ctypes.c_uint64` |
| `uint8_t*` | `ctypes.POINTER(ctypes.c_uint8)` |

## Validierungscheckliste

```pseudo
validate_call(function, config):
    assert gpu_initialized
    assert required_bytes(config) <= global_mem * 0.65
    assert all_buffers_non_null
    assert buffer_sizes_match_contract
    assert numerical_parameters_in_safe_range
    if function == scheduler:
        assert active_count_is_zero
```

## Erklärungspflicht

Wenn ein Agent Parameter automatisch reduziert, muss er an den Nutzer melden:

- ursprüngliches N/K/C/R/q
- geschätzter VRAM
- verfügbare sichere Grenze
- neue reduzierte Werte
- welche Domäne deaktiviert oder sequenziell geplant wurde
