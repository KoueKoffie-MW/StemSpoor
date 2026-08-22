# MOD-06 — Modular Architecture

**Status:** Draft  
**Priority:** Medium-High  
**Phase:** Phase 3  
**Parent:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Phase 3)  
**Siblings:** MOD-01, MOD-02, MOD-03, MOD-04, MOD-05  
**Index:** [INDEX.md](../INDEX.md)

---

## Objective

Split the current monolithic `:app` module into a set of focused, reusable feature modules. This improves build times, testability, parallel development, and long-term maintainability.

---

## Current State

- Single `:app` module.
- All code lives under one package tree.
- Long build times when making small changes.
- Difficult to reason about boundaries between recording, AI, vault, and sync.

---

## Detailed Requirements

- Split into logical feature + core modules.
- Clear dependency graph (no cycles).
- Core modules should be UI-agnostic where possible.
- Feature modules can depend on core but not on each other directly (or only via well-defined interfaces).
- Existing code can be migrated incrementally.

---

## Proposed Module Structure (Pragmatic 4-Module Layout)

To avoid excessive Gradle sync overhead and circular dependency bloat on Windows, the project adopts a clean, high-leverage **4-Module Architecture**:

```
                  ┌──────────────────────┐
                  │        :app          │  (UI, Compose, Screens, Navigation)
                  └──────────┬───────────┘
                             │
            ┌────────────────┼────────────────┐
            ▼                ▼                ▼
   ┌────────────────┐┌────────────────┐┌────────────────┐
   │  :core:audio   ││    :core:ai    ││   :core:data   │
   │ (Silero VAD,   ││(Whisper, Gemma,││ (Room DB,      │
   │  Voice Gate,   ││ Sherpa-ONNX,   ││  Repositories, │
   │  AudioRecord)  ││ Embeddings)    ││  Sidecars/Vault│
   └────────────────┘└────────────────┘└────────────────┘
```

### Module Responsibilities:
1. **`:app`**: Pure presentation and orchestration. Jetpack Compose UI, Navigation, Activity, Status Bar theming.
2. **`:core:audio`**: Low-level audio recording engine. 16kHz PCM capture, Silero VAD state machine, 10s circular verification buffer, and Voice Gate hardware hook.
3. **`:core:ai`**: On-device neural intelligence. ONNX Runtime Mobile, Whisper ASR, Sherpa-ONNX 3D-Speaker embeddings, Gemma 4 / llama.cpp polishing, and multilingual E5 semantic search embeddings.
4. **`:core:data`**: Storage and persistence. Room SQLite database, DataStore configuration, JSON sidecar atomic serializers, and Obsidian Markdown vault file exports.

**Rationale:** Captures 95% of modularity benefits (strict separation of concerns, testability, and reuse for potential Wear OS / CLI targets) without the maintenance friction of 8+ micro-modules.

---

## Implementation Steps

1. Define module boundaries and dependency rules in a new `ARCHITECTURE.md`.
2. Extract `:core` module (data classes, interfaces, utils).
3. Move storage and Room code into a `:data` module.
4. Create feature modules one by one, starting with the most independent.
5. Update Hilt modules to live in the correct modules.
6. Gradually move UI into feature modules or keep a thin `:app` + `:ui-common`.

---

## Dependencies

- Strongly benefits from MOD-01 (clean repositories and use cases make splitting easier).
- Should happen after the major data/AI refactors in Phase 2 to avoid moving unstable code.

---

## Risks & Open Questions

- Gradle module complexity on Android (especially with ONNX native libs).
- Hilt + multi-module setup.
- How much UI to split vs keep in app.

**Recommendation:** Start with a modest split (`:core`, `:data`, `:domain`) before aggressive feature modules.

---

## Acceptance Criteria

- Project builds with at least 3–4 modules.
- Clear dependency rules are documented and enforced (where possible).
- Feature modules can be developed somewhat independently.
- Build time improvement is measurable on clean + incremental builds.

---

## References

- Main plan Phase 3.
- Android best practices for modularization (check https://developer.android.com/topic/modularization for latest guidance — agent knowledge may be outdated).

---

**Read the main MODERNIZATION_PLAN.md and MOD-01 before starting implementation.**