# k6-tests Specification

## Purpose

The black-box k6 scenario suite that proves the challenge requirements — concurrency, idempotency, consistency, validation, transfer atomicity — against the running REST API (`rest-api` spec, ADR-0008/0043), with deterministic assertions and `checks: rate==1` thresholds so any violation fails the run.

## ADDED Requirements

### Requirement: Scenario package layout

The suite SHALL live in a top-level `k6/` package with a shared HTTP client and shared contract assertions in `k6/lib/`, one script per concern in `k6/scenarios/`, a Persian `k6/README.md` mapping each challenge requirement to the scenario proving it, and a `just k6 <scenario>` task. The base URL SHALL be overridable via the `BASE_URL` environment variable, defaulting to `http://localhost:8080`.

#### Scenario: Run any scenario with one command

- **WHEN** a developer runs `just k6 idempotency` (or `k6 run k6/scenarios/idempotency.js`)
- **THEN** the scenario executes against `BASE_URL` and exits non-zero if any check fails

### Requirement: Validation proofs

`validation.js` SHALL assert every validation rule of the challenge with balance-unchanged proofs: `amount <= 0` rejected on credits/debits/transfers, unknown account rejected on all operations, same-account transfer rejected, missing `Idempotency-Key` rejected, malformed JSON rejected, and insufficient-funds debit rejected — each with the status and problem `code` defined by the `rest-api` spec, plus `application/problem+json` content type, snake_case fields, `Cache-Control: no-store` on balance reads, and `X-Flow-ID` echo on success and error responses.

#### Scenario: Every rejection leaves balances unchanged

- **WHEN** `validation.js` runs against the service
- **THEN** each invalid request returns the spec'd status and `code`, and a balance re-read after each rejection equals the pre-request balance

### Requirement: Idempotency proofs

`idempotency.js` SHALL prove exactly-once effect for credit, debit, and transfer both sequentially (same `Idempotency-Key` ×3, balance moves once, later responses are `REPLAYED` with `Idempotency-Replayed: true`) and concurrently (many VUs firing the same `Idempotency-Key` simultaneously, final balance reflecting exactly one application).

#### Scenario: Concurrent duplicate storm applies once

- **WHEN** 50 VUs concurrently submit the same `Idempotency-Key` for a credit, a debit, and a transfer
- **THEN** the final balances show exactly one application of each operation and no error responses other than replays

### Requirement: Concurrency proofs

`concurrency-single-account.js` SHALL prove that of two concurrent `debit(700)` on a balance of 1000 exactly one succeeds (final balance 300), and that 1,000 concurrent debits of 100 against a 100,000 balance end at exactly 0 with failures limited to `INSUFFICIENT_FUNDS`. `concurrency-multi-account.js` SHALL prove independent accounts do not block each other: each VU operates exclusively on its own account pair, every pair's balance sum stays constant, and throughput stays within the declared thresholds.

#### Scenario: Race pair resolves deterministically

- **WHEN** two VUs concurrently debit 700 from an account holding 1000
- **THEN** the final balance is exactly 300 — never negative, never 1000 minus both

#### Scenario: Thousand concurrent debits drain exactly

- **WHEN** 100 VUs fire 1,000 debit-100 operations against a 100,000 balance without pacing delays
- **THEN** the final balance is exactly 0 and no negative balance or lost update is observable

### Requirement: Transfer atomicity proofs

`transfer-atomicity.js` SHALL prove atomicity two ways: a ping-pong scenario whose teardown asserts exact net-zero final balances, and a conservation monitor scenario that continuously re-reads both accounts during the transfer storm and fails if the observed sum ever deviates from the funded total (no observable partial transfer).

#### Scenario: Partial transfer is never observable

- **WHEN** the monitor reads both balances throughout the ping-pong storm
- **THEN** every observation satisfies `balance(A) + balance(B) == initial sum`
