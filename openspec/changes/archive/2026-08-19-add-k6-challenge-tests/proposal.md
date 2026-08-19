# Proposal: k6 challenge-proof test scenarios

## Why

The challenge (`docs/Coding_Challenge_V2_English.md`) grades correctness under concurrency, idempotency, consistency, transfer atomicity, and validation — and explicitly asks for tests that demonstrate correctness under concurrency with deterministic assertions, including one that increases the probability of race conditions. Unit/contract tests exist, but nothing proves the guarantees end-to-end against the running HTTP service (ADR-0008, ADR-0043). The REST driving adapter is complete, so a black-box load/behavior suite can now drive the real flow.

## What Changes

- **New `k6/` package** at project root (plain k6 scripts, no npm dependencies):
  - `config.js` — `BASE_URL` from env, default `http://localhost:8080`.
  - `lib/client.js` — thin HTTP client: createAccount, credit, debit, transfer, getBalance; unique `Idempotency-Key` generation.
  - `lib/assert.js` — shared checks: APPLIED/REPLAYED outcomes, problem+json assertions on the stable `code` member, `X-Flow-ID` echo, `Cache-Control: no-store`.
  - `scenarios/smoke.js` — happy-path gate.
  - `scenarios/validation.js` — invalid amount, unknown account, same-account transfer, missing Idempotency-Key, malformed JSON, insufficient funds, error-contract and header conventions.
  - `scenarios/idempotency.js` — sequential ×3 duplicates for credit/debit/transfer, plus concurrent duplicate storms (same `Idempotency-Key` from many VUs) proving exactly-once balance effect.
  - `scenarios/concurrency-single-account.js` — the challenge's `debit(A,700)×2` race pair (final balance exactly 300) and the reference shape 100,000 balance / 1,000 concurrent debits of 100 (final balance exactly 0).
  - `scenarios/concurrency-multi-account.js` — 25 VUs each owning a private account pair, proving independent accounts don't block (exact conservation per pair + throughput thresholds).
  - `scenarios/transfer-atomicity.js` — ping-pong transfers with exact net-zero teardown assertion plus a parallel conservation monitor that re-reads both balances continuously and fails on any observable partial transfer.
  - `README.md` (Persian per docs-fa) — install/run instructions and a mapping of each challenge requirement to the scenario that proves it.
- **`justfile`**: `just k6 <scenario>` task.
- Scripts are delivered without execution (persistence adapter still in development); each scenario sets `checks: ['rate==1']` thresholds so a run fails loudly on any violation.

## Capabilities

### New Capabilities

- `k6-tests`: black-box k6 scenario suite proving the challenge requirements (concurrency, idempotency, consistency, validation, transfer atomicity) over the live REST API.

### Modified Capabilities

(none)
