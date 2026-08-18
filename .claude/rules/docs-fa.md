# Rule: Persian documentation (README + ADR)

## README.md

- Written in **Persian (فارسی)** and **concise** — no filler, no marketing.
- Must answer the challenge's required sections:
  1. **Architecture** — overall structure, main components.
  2. **Concurrency** — how thread safety is ensured, why this approach, what happens to same-account operations under concurrency.
  3. **Idempotency** — how double-execution of a transaction is prevented; behavior when the same transaction arrives concurrently.
  4. **Transfer** — how atomicity is guaranteed; is deadlock possible? If yes, under what conditions; if no, how it is prevented.
  5. **Same-account transfer** — the defined behavior for `transfer(A, A, ...)` and the reasoning.
  6. **Technology choices** — for every optional technology used (DB, Redis, Kafka, Docker, …): why it was used, what problem it solves, what trade-offs it introduces.
  7. **Build/run/test** — the standard command (`./mvnw test`) and any required dependency or configuration.
  8. **Scope honesty** — if anything is unfinished due to time: what is implemented, what remains, and how it would be continued.

## ADRs (Architecture Decision Records)

- Live in `docs/adr/`, named `NNNN-kebab-title.md` (zero-padded sequence).
- Written in **Persian (فارسی)**.
- Follow the standard template in `docs/adr/000-template.md` (Nygard style): عنوان، وضعیت، زمینه، تصمیم، پیامدها.
- One ADR per significant decision (concurrency strategy, idempotency storage, transfer locking, same-account-transfer semantics, any optional infrastructure).
- Status flow: پیشنهادی → پذیرفته‌شده (or ردشده / منسوخ with a superseding link). Never edit a پذیرفته‌شده ADR's decision — supersede it.
