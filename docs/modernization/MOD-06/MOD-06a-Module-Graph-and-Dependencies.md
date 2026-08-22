# MOD-06a — Module Graph and Dependencies Specification

**Parent:** [MOD-06-Modular-Architecture.md](../MOD-06-Modular-Architecture.md)  
**Status:** Approved  
**Priority:** Critical

---

## Module Definitions

| Module | Type | Purpose | Dependencies |
| :--- | :--- | :--- | :--- |
| `:core:model` | JVM / Kotlin Library | Domain entities, Sidecar data schemas, Enums, Gate decision contracts | None |
| `:core:database` | Android Library | Room Database, SQLite DAOs, Entity tables, TypeConverters | `:core:model`, Room, KSP |
| `:core:audio` | Android Library | Hardware `AudioRecord`, Silero VAD, Opus compression, WAV writer | `:core:model` |
| `:core:ai` | Android Library | sherpa-onnx, CAM++ embeddings, Voice Gate evaluator, Gemini API client | `:core:model`, `:core:database`, sherpa-onnx |
| `:core:vault` | Android Library | Obsidian vault synchronization, Wikilink parser, Markdown formatter | `:core:model`, `:core:database`, `:core:ai` |
| `:feature:record` | Android Library | Recording foreground service, Live audio visualizer, Quick tile controller | `:core:model`, `:core:audio`, `:core:ai` |
| `:feature:recordings` | Android Library | Recordings list, Audio playback engine, Transcript viewer, Speaker chips | `:core:model`, `:core:database`, `:core:vault` |
| `:feature:settings` | Android Library | Settings screens, Voice enrollment UI, Storage paths, Model downloads | `:core:model`, `:core:database`, `:core:ai`, `:core:vault` |
| `:app` | Android Application | `Application` entrypoint, Splash screen, Navigation compose host, Koin root | All `:feature:*` and `:core:*` |
