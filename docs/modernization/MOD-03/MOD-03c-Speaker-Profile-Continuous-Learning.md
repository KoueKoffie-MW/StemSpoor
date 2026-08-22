# MOD-03c — Speaker Profile Continuous Learning Spec

**Parent:** [MOD-03-Speaker-Embeddings.md](../MOD-03-Speaker-Embeddings.md)  
**Status:** Approved

---

## Continuous Centroid Updating
- For segments matching an enrolled profile with confidence $\ge 0.82$:
  - Calculate running exponential moving average:
    $$\mathbf{c}_{new} = \alpha \mathbf{c}_{old} + (1 - \alpha) \mathbf{e}_{segment}$$
  - Maintain separate language centroids for Afrikaans, English, German.
