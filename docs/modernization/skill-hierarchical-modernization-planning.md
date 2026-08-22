# Skill: `hierarchical-modernization-planning`

**Use this skill when:** You need to create a granular, multi-level modernization/implementation plan that is modular, linkable, and hand-off-ready. The user (a MathWorks engineer and Calvinist Afrikaner) works on Android + engineering projects and wants strict separation of concerns, narrow "one thing per plan" documents, parallel branch structure mirroring each level, and full preservation of "big picture" context across levels.

## Philosophy & Constraints

- **Parallel branches at every level.** Never one giant doc. Level 2 should split into a-focused and b-focused docs; Level 3 continues a/b (and adds c, d, e, f, g as needed).
- **One thing per plan.** Each implementation plan must cover ONLY one feature/fix/area. Never bundle unrelated work.
- **Big picture survives the descent.** Always keep main plan + parent levels + relevant siblings in context. Link both ways (parent→child and child→parent + siblings).
- **Hand-off ready.** Each plan should contain enough that another agent (or a future session) can execute it without context loss.
- **Recent code changes must be preserved, not overwritten.** Note known recent changes (e.g. logo added, segment merge gap setting, colors updated) explicitly in relevant plans.
- **Never stop at planning.** Always produce a real artifact (file + structure), then report concrete verification of what got produced.
- **Version-check guardrails.** For any tool/library, always include official docs links and a note: "agent/user knowledge may be outdated — verify latest version at <official-site URL>."
- **English only for code, comments, and technical content.** Afrikaans may be used for personal/philosophical anchors only.
- **Clean up monoliths.** If you previously created a giant "complete implementation plan" doc, delete it and split into narrow ones.

## Workflow (Step-by-Step)

1. **Anchor to project**
   - If working in a known project dir (e.g. `Y:\AntiGravity\Android_Apps\recme`), treat it as the workspace; the project was created via `project_create` and is the active project.
   - If no active project matches, use `project_create` or work from the project's `docs/` folder.

2. **Read the stack top-down (minimum)**
   - Main `MODERNIZATION_PLAN.md` (or equivalent master plan).
   - Level 1 sub-plan index (`docs/modernization/INDEX.md`).
   - The specific Level 1 sub-plan you are descending into (e.g. `MOD-01-PHASE1-Foundations.md`).
   - Relevant Level 2 docs if they exist.

3. **Decide the split at each level**
   - Ask: does this level need a single focused treatment (a) or a parallel treatment (b)?
   - For implementation (Level 3+): prefer one-narrow-doc-per-thing. Name them sequentially within their branch letter (e.g. MOD-01c, d, e, f, g...).

4. **Create each narrow doc with this header template**
   ```markdown
   # <PLAN_TITLE> (Level <N>)

   **Parent Level <(N-1)>:** [link]
   **Parent Level <(N-2)> (if applicable):** [link]
   **Focus:** One thing only — <single sentence of scope>.
   **Status:** Draft (Implementation Plan)
   **Date:** <% current date %>

   **Important:** Narrow scope. Out of scope list: <enumerate sibling plans>.

   **Recent Code Changes to Respect/Preserve:**
   - <List each known code change relevant to THIS narrow scope.>
   - <At minimum, if none exist yet, state: "No recent code changes known; verify against current repo state at hand-off.">

   **Tool Version Note:** Check latest version at <official docs URL> (knowledge may be outdated).
   ```

5. **Write the body using these sections (always)**
   - ## Scope (One Thing) — explicit sentence + out-of-scope refs to other narrow plans.
   - ## Current State Snapshot — short, specific to this scope.
   - ## Steps (Granular, One at a Time) — numbered; each step must be individually verifiable.
   - ## Acceptance Criteria (This Scope Only) — bullet list, testable.
   - ## Links to Other Pieces — which parent/sibling plans depend on this.
   - ## Hand-off Instructions (optional, for the most complex narrow plan) — short.

6. **Create an overview doc at that level**
   - `MOD-<NN>-Level<LEVEL>-Implementation-Overview.md` summarizing the parallel structure and how to navigate.

7. **Propagate upward after creating**
   - Patch the parent Level-(N-1) doc to add a section referencing the new narrow plans (with the "one thing at a time" framing).
   - Update `INDEX.md` to list the new Level-N docs under the deeper-layers section for that area, including a note that each is narrowly scoped and preserves recent code changes.
   - Update `MODERNIZATION_PLAN.md` with a short "deeper activity" entry.

8. **Verify at the end**
   - Confirm the full file tree under `docs/modernization/MOD-NN` is consistent (no stale monolithic docs).
   - Confirm each parent has a back-link to its child(ren) and vice-versa.
   - Report the final structure to the user as a real artifact.

## Templates

### Narrow implementation plan file (MOD-NNc-Name.md)

(See step 5 body structure above; replicate the template literally.)

### Level overview file

(See "MOD-01-Level3-Implementation-Overview.md" for the canonical structure.)

## Acceptance Checks (for this skill itself)

After running:
- [ ] There exists a single master-level "deeper activity" mention in the root MODERNIZATION_PLAN.md.
- [ ] There exists an INDEX.md update describing the new deeper docs.
- [ ] There exists an overview doc at the deepest level describing the parallel a/b/c... split.
- [ ] Each narrow doc is self-contained with Parent links, Scope (one thing), Steps, and Acceptance Criteria.
- [ ] Each narrow doc explicitly preserves / references known recent code changes (logo, segment merge gap, brand colors) when relevant.
- [ ] Stale monolithic docs (if any were created at this level) have been removed.

## Memory/Reuse Notes

- The 3 hard-facts you should always remember about this user's recme project: (1) logo added, (2) segment merge gap setting added, (3) brand colors updated to match logo.
- This skill is meant to be reusable across projects (Android, MATLAB, Unreal, web). Replace project-specific file names but keep the structure and guardrails.
- If a level 1 sub-plan already exists and is large, do not rewrite it — only add a short "deeper activity" reference.

## Troubleshooting

- **Symptom:** patch tool fails with "path required" or "duplicate tool output".
  **Fix:** switch to `terminal` append (`cat >> file`) for propagation edits, then resume patching.
- **Symptom:** project not anchored.
  **Fix:** call `project_switch` or note the absolute path and continue; do not fail.
- **Symptom:** tempted to write one big plan.
  **Fix:** STOP. Split by branch letter. One thing per doc. Always.
