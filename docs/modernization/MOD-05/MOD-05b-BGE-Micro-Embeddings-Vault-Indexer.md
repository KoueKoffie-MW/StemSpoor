# MOD-05b — BGE-Micro Embeddings & Vault Indexer Spec

**Parent:** [MOD-05-Semantic-Search-Vault.md](../MOD-05-Semantic-Search-Vault.md)  
**Status:** Approved

---

## Vault Parsing & Link Extraction
- Observes Obsidian Markdown directory.
- Parses `[[Wikilinks]]` and YAML frontmatter tags (`#project`, `#meeting`).
- Emits bidirectional backlink graph to highlight related voice recordings in Obsidian notes.
