# MOD-06 — Modular Architecture (8 Gradle Modules)

**Status:** Approved (Immediate Execution)  
**Priority:** Critical  
**Phase:** Immediate Pre-requisite for Phase 2b  
**Related:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md), [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)

---

## Objective
Decompose the monolithic `:app` module into 8 isolated, highly testable, and compile-optimized Gradle modules. Prevents circular dependencies, isolates native dependencies (sherpa-onnx, whisper.cpp, sqlite-vec), and speeds up incremental build times.

---

## Module Hierarchy & Topology

```
                  ┌───────────────┐
                  │     :app      │ (Application, NavHost, Koin Composition)
                  └───────┬───────┘
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌──────────────────┐ ┌──────────┐ ┌────────────────┐
│:feature:recordings│ │:feature: │ │:feature:       │
│                  │ │  record  │ │  settings      │
└────────┬─────────┘ └────┬─────┘ └───────┬────────┘
         │                │               │
         └────────────────┼───────────────┘
                          ▼
                  ┌───────────────┐
                  │  :core:vault  │ (Obsidian sync, wikilinks, markdown)
                  └───────┬───────┘
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
┌──────────────────┐ ┌──────────┐ ┌────────────────┐
│    :core:ai      │ │:core:    │ │:core:database  │
│(sherpa, gemini)  │ │  audio   │ │(Room, DAOs)    │
└────────┬─────────┘ └────┬─────┘ └───────┬────────┘
         │                │               │
         └────────────────┼───────────────┘
                          ▼
                  ┌───────────────┐
                  │  :core:model  │ (Entities, Sidecar schema, Value objects)
                  └───────────────┘
```

---

## Deeper Implementation Documents (Level 2 & 3)
- [MOD-06a-Module-Graph-and-Dependencies.md](MOD-06/MOD-06a-Module-Graph-and-Dependencies.md) — Exact Gradle dependency graph and version catalog aliases
- [MOD-06b-Core-Modules-Migration.md](MOD-06/MOD-06b-Core-Modules-Migration.md) — Core layer extraction (:core:model, :core:database, :core:audio, :core:ai, :core:vault)
- [MOD-06c-Feature-Modules-Migration.md](MOD-06/MOD-06c-Feature-Modules-Migration.md) — Feature layer extraction (:feature:record, :feature:recordings, :feature:settings, :app)
