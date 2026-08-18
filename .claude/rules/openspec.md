# Rule: OpenSpec workflow (mandatory for all features/implementations)

Every feature, behavior change, or implementation task in this project goes through OpenSpec. **No code is written without an approved change proposal.**

## Workflow

1. **Explore (optional)** — `/opsx:explore` for no-stakes thinking before committing to a proposal.
2. **Propose** — `/opsx:propose` creates a change folder in `openspec/changes/<name>/` containing:
   - `proposal.md` — why and what
   - `design.md` — how (when the change has design weight)
   - `tasks.md` — implementation checklist
   - `specs/` — spec deltas
3. **Apply** — `/opsx:apply` implements the checklist in `tasks.md`.
4. **Archive** — `/opsx:archive` moves the completed change to `openspec/changes/archive/` and merges its deltas into the living specs.

## Directory semantics

- `openspec/specs/` — the living source of truth for current requirements.
- `openspec/changes/` — in-flight work only.
- `openspec/changes/archive/` — completed history.

## Spec format

- Specs are plain Markdown with **concrete scenarios**: every requirement includes `WHEN … THEN …` style scenarios.
- Requirements must be unambiguous and testable — no "should be fast" without a measurable criterion.

## Philosophy

- Fluid, not rigid; iterative, not waterfall.
- Keep context windows clean — one change at a time, archive when done.
- If agent guidance or slash commands feel stale after an upgrade, run `openspec update`.
