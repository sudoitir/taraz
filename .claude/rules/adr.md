# Rule: ADRs are binding

All architecture decision records live in **`docs/adr/`** — treat that directory as a whole as the binding law of this project. (Individual ADRs are deliberately not enumerated here; read the directory.)

## Mandatory

- **Follow the ADRs.** Design, code, dependencies, and APIs must comply with every accepted (پذیرفته‌شده) ADR in `docs/adr/`.
- **Before proposing or implementing**, skim the ADR directory for decisions touching your scope (stack, architecture, persistence, events, testing, …).
- **Conflicts are flagged, not improvised.** If a task conflicts with an ADR, stop and surface it — never silently work around a recorded decision.
- **New significant decision → new ADR.** Adding a technology, changing a pattern, or deviating from a convention requires a new ADR in `docs/adr/` (next sequential number, Persian, `<div dir="rtl">`, per `.claude/rules/docs-fa.md`) — before or with the change, not after.
- **Never edit an accepted ADR's decision** — supersede it with a new ADR and mark the old one منسوخ with a link to its replacement.
- OpenSpec proposals must reference the ADRs they depend on (see `openspec/config.yaml` rules).
