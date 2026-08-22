# MOD-05a — Vector & FTS5 Hybrid Search Design

**Parent:** [MOD-05-Semantic-Search-Vault.md](../MOD-05-Semantic-Search-Vault.md)  
**Status:** Approved

---

## Search Architecture
1. **FTS5 Table:** `CREATE VIRTUAL TABLE transcript_fts USING fts5(recordingId, text, speakerName);`
2. **Vector Index:** `VaultIndexEntity` storing 384-d normalized vector arrays in SQLite.
3. **Rank Fusion:** Reciprocal Rank Fusion (RRF) combining FTS5 lexical hits and vector cosine distances.
