# MOD-05 — Local Semantic Search & Obsidian Vault Intelligence

**Status:** Approved  
**Priority:** Medium-High  
**Phase:** Phase 2  
**Related:** [MOD-04-Transcription-Pipeline.md](MOD-04-Transcription-Pipeline.md), [MOD-06-Modular-Architecture.md](MOD-06-Modular-Architecture.md)

---

## Objective
Provide an ultra-fast **Dual Search Engine** combining **Room FTS5 full-text keyword indexing** with **local 384-d vector embeddings (BGE-Micro / MiniLM ONNX ~20MB)** to enable natural semantic queries across all audio transcripts and Obsidian vault markdown notes.

---

## Sub-Plans
- [MOD-05a-Vector-FTS5-Search-Design.md](MOD-05/MOD-05a-Vector-FTS5-Search-Design.md) — Hybrid retrieval architecture (BM25 + Cosine similarity)
- [MOD-05b-BGE-Micro-Embeddings-Vault-Indexer.md](MOD-05/MOD-05b-BGE-Micro-Embeddings-Vault-Indexer.md) — Vector indexing & bidirectional wikilink graph
