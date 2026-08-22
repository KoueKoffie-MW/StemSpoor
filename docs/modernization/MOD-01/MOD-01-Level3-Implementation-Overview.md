# MOD-01 Level 3 — Implementation Plans Overview (Granular, One Thing at a Time)

**Parent:** [MOD-01-PHASE1-Foundations.md](../MOD-01-PHASE1-Foundations.md)  
**Mirrors Level 2 structure:** Level 2 has MOD-01a (model/consent) and MOD-01b (schema+repos+migration).  
Level 3 provides separate, narrow implementation plans — one per feature/fix/area.

**Rule:** Each Level 3 document covers **only one thing**. This makes the pack complete, linkable, and easy to hand off or review.

## Parallel Structure (to match higher levels)

**Branch A-style (Setup, Integration, Branding — parallel to model/consent focus):**
- [MOD-01c-Hilt-DI-Implementation.md](MOD-01c-Hilt-DI-Implementation.md)
- [MOD-01g-Branding-Logo-Color-Integration.md](MOD-01g-Branding-Logo-Color-Integration.md)

**Branch B-style (Data Layer Details — parallel to schema/repos/migration):**
- [MOD-01d-Room-Schema-Implementation.md](MOD-01d-Room-Schema-Implementation.md)
- [MOD-01e-Repositories-Implementation.md](MOD-01e-Repositories-Implementation.md)
- [MOD-01f-Migration-Bootstrap-Implementation.md](MOD-01f-Migration-Bootstrap-Implementation.md)  
  (Includes handling for the recent segment merge gap setting change)

## How to Use
1. Read Level 1 (MOD-01) and relevant Level 2 (01a or 01b).
2. Pick the matching Level 3 narrow plan.
3. Implement only that scope.
4. When done, update the plan and propagate any decisions upward (edit parents to note resolutions).

All plans note the 3 recent code changes (logo, segment merge threshold setting, brand colors) where relevant.

**Latest tool versions:** Always cross-check official docs (e.g. https://developer.android.com for Hilt/Room, https://developer.android.com/jetpack/compose for theming).

This structure keeps the entire document pack modular, complete, and useful for deep planning or hand-off to other agents.
