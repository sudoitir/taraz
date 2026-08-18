# Rule: Persian documentation (README + ADR)

## How to write the Persian text (applies to README.md, ADRs, all Persian docs)

- **Fluent, natural Persian** — write as a Persian-speaking senior engineer would, not word-by-word translation from English. Short sentences. Easy to read aloud.
- **Keep technical terms in English** — do NOT force-translate: API, HTTP, REST, CQRS, DDD, event, outbox, lock, transaction, thread, idempotency (or idempotent), cache, index, commit, merge, framework, library, container, migration, test, build, deploy, header, UUID, … Use the English word inline in the Persian sentence. Translate only what has a natural, established Persian equivalent (معماری، دامنه، موجودی، تراکنش، قانون، تصمیم، …).
- **Mixed is normal**: «تراکنش در همان transaction دیتابیس ثبت می‌شود» is better than inventing awkward Persian for every term.
- **No calques**: avoid literal English structures («باید توجه داشت که…» chains, passive piles). Prefer direct active Persian.

## RTL / LTR layout (mandatory)

- Wrap all Persian content in `<div dir="rtl"> … </div>` (with blank lines inside so markdown renders).
- Code blocks, shell commands, and any LTR-only fragment (URLs list, JSON, stack traces) go inside `<div dir="ltr">` when they appear inside RTL text, or simply keep fenced code blocks as-is — never mix LTR code into an RTL paragraph.
- Inline English terms inside Persian sentences need no wrapper — only block-level direction switches do.

## README.md

- Written in **Persian (فارسی)**, **concise** — no filler, no marketing.
- Must answer the challenge's required sections:
  1. **Architecture** — overall structure, main components.
  2. **Concurrency** — how thread safety is ensured, why this approach, what happens to same-account operations under concurrency.
  3. **Idempotency** — how double-execution of a transaction is prevented; behavior when the same transaction arrives concurrently.
  4. **Transfer** — how atomicity is guaranteed; is deadlock possible? If yes, under what conditions; if no, how it is prevented.
  5. **Same-account transfer** — the defined behavior for `transfer(A, A, ...)` and the reasoning.
  6. **Technology choices** — for every optional technology used: why, what problem it solves, what trade-offs.
  7. **Build/run/test** — the standard command (`./mvnw test`) and any required configuration.
  8. **Scope honesty** — if anything is unfinished: what is implemented, what remains, how it would continue.

## ADRs (Architecture Decision Records)

- Live in `docs/adr/`, named `NNNN-kebab-title.md` (zero-padded sequence).
- Written in **Persian**, wrapped in `<div dir="rtl">`, following `docs/adr/000-template.md` (Nygard style: عنوان، وضعیت، زمینه، تصمیم، پیامدها).
- **One ADR per decision.** Skeleton first (short); full explanation later in the ADR's dedicated session.
- Status flow: پیشنهادی → پذیرفته‌شده (or ردشده / منسوخ with a superseding link). Never edit a پذیرفته‌شده ADR's decision — supersede it.
