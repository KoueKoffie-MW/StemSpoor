# MOD-05 — Local Semantic Search & Vault Intelligence

**Status:** Draft  
**Priority:** Medium-High  
**Phase:** Phase 2  
**Parent:** [MODERNIZATION_PLAN.md](../../MODERNIZATION_PLAN.md) (Phase 2)  
**Siblings:** [MOD-02](../MOD-02-VoiceGate-Privacy-Legal.md), [MOD-03](../MOD-03-Speaker-Embeddings.md), [MOD-04](../MOD-04-Transcription-Pipeline.md)  
**Index:** [INDEX.md](../INDEX.md)

---

## Objective

Turn the Obsidian vault from a searchable collection of markdown files into a true **local cognitive second brain** with fast full-text + semantic search, topic linking, and intelligent "Ask AI" capabilities — all running offline.

---

## Current State

- Vault output is high-quality Obsidian markdown with `[[wiki links]]`, daily notes, speaker tags, and action items.
- Search is currently limited to string scanning or Obsidian's built-in search.
- "Ask AI" exists using Gemma over the vault, but it is basic and re-processes content on every query.
- No embeddings or vector index.
- No dedicated vault index database.

---

## Detailed Requirements

### Must Have
- Generate and store text embeddings for polished transcripts and vault notes.
- Fast semantic search ("What did we say about the kids' school last month?").
- Hybrid search: semantic + full-text (Room FTS + vector).
- Improve the existing "Ask AI" chat to use retrieved context (RAG-style, local only).
- Automatic topic extraction and better `[[wiki links]]` suggestions.

### Should Have
- On-device embedding model (small, quantized).
- Incremental indexing (only new/changed content).
- Speaker-aware and date-aware filtering in search.
- Action item extraction improvements with semantic grouping.

### Nice to Have
- Cross-lingual search (Afrikaans ↔ English ↔ German).
- Visual topic maps or graph views (future).

---

## Proposed Design

**Storage**
- Use Room for a `VaultIndex` table:
  - `content_id`, `type` (transcript / daily_note / topic_page), `text`, `embedding`, `metadata` (date, speaker, language)
- Embeddings stored as BLOB or normalized float arrays.
- Sidecars / markdown remain source of truth.

**Embedding Model (Selected Option): `multilingual-e5-small` (INT8 ONNX)**
- **Coverage:** Robust 100-language support (specifically strong in Afrikaans, English, German code-switching).
- **Dimensions:** 384-dimensional dense vectors.
- **Inference Runtime:** Runs directly on `onnxruntime-android` (reusing the existing runtime already loaded for Silero VAD).
- **Format:** Quantized `qint8` (~40MB total storage footprint).
- **Prompt Structure:** Asymmetric prefixing (`query: <user question>` vs `passage: <transcript chunk>`) ensures state-of-the-art retrieval accuracy.

**Query Flow**
1. User query → embed query
2. Vector similarity search in Room
3. Optional keyword boost / filters
4. Pass top-k chunks + original query to local Gemma/LLM for answer

**RAG for "Ask AI"**
- Retrieve relevant segments first, then synthesize answer.
- Store conversation history locally.

---

## Implementation Steps

1. Define `VaultIndex` entity and DAO (coordinate with MOD-01 Room work).
2. Create `EmbeddingService` (pluggable, similar to speaker embeddings).
3. Build indexing pipeline (triggered after transcription + polishing).
4. Implement semantic search repository.
5. Upgrade `VaultChatManager` / Ask AI to use retrieval.
6. Add UI search screen with filters (speaker, date range, semantic vs keyword).
7. Add incremental re-indexing and embedding model management.

---

## Dependencies

- Depends on: MOD-01 (Room foundations)
- Works well with: MOD-04 (better polished transcripts = better embeddings)
- Enables: Much stronger vault experience (Phase 4 UX)

---

## Risks & Open Questions

| Risk / Question | Notes |
|-----------------|-------|
| Embedding model quality on Afrikaans + mixed language | Test multiple models. May need fine-tuning or domain-specific data. |
| Storage size of embeddings | Quantize embeddings (INT8/INT4) and use approximate nearest neighbor if needed. |
| When to re-embed content | Version embeddings + re-embed on model change. |
| Privacy of embeddings | They are derived from personal data — treat same as transcripts. |

---

## Acceptance Criteria

- Semantic search returns relevant results in < 1 second for months of data.
- "Ask AI" uses retrieved context and gives better answers than before.
- Indexing happens automatically after new recordings are processed.
- User can filter searches by speaker/date.
- All processing is fully local.

---

## References & Tool Notes

- Main plan: Phase 2 → "Add local semantic search"
- Related ADRs: 0011 (In-app Obsidian vault and Gemma AI chat)
- For latest on-device embedding models and ONNX export: https://huggingface.co/sentence-transformers and https://onnxruntime.ai/
- llama.cpp / Gemma updates: https://github.com/ggerganov/llama.cpp (always check current version)

---

**This sub-plan is intended for specification-driven review. When implementing, always re-read the main MODERNIZATION_PLAN.md and relevant sibling sub-plans for context.**