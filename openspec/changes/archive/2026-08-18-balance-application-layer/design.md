## Context

See `proposal.md` — Why. Binding constraints from `docs/adr/`: 0005 (Result, not exceptions), 0006 (ports = interfaces only, driving adapters never see outbound ports), 0007 (CQRS: one package per write use case, stateless read side never routed through service), 0011 (business rules as Specifications), 0018 (transaction opens at the true atomic unit, in the command handler), 0021 (Valkey idempotency gate + DB unique-constraint shield), 0026 (`SELECT … FOR UPDATE`, ordered by `accountId`, checked after lock), 0028 (self-transfer rejected before any lock/transaction, id unconsumed), 0031 (no Lombok/MapStruct/annotation-processing in `core`), 0033 (module dependency direction), 0034 (commands are the only input, jakarta validation on the command, idempotency owned by the handler), 0036 (`long → Money` at the application edge), 0037 (atomic unit = N accounts + 1 Transaction).

One concrete tension in the current ADR set drives this design: **ArchUnit's `core_does_not_depend_on_spring` rule is scoped to `..core..`**, which would forbid `@Transactional` anywhere in `core/application/service` — but ADR-0018 puts the transaction boundary exactly there. This design resolves it (Decision D1/D2) rather than improvising around it, and records the resolution as new ADRs per `.claude/rules/adr.md`.

## Goals / Non-Goals

**Goals:**
- A complete, callable write side (credit/debit/transfer) and read side (getBalance) proven with in-memory fakes, satisfying every scenario in `specs/balance-application-layer/spec.md`.
- A transaction-boundary mechanism that keeps `core/application/service` framework-free with respect to persistence while still allowing Spring stereotypes.
- An idempotency design that has no unresolved crash window (unlike ADR-0021's `IN_PROGRESS` state read in isolation).
- Ports shaped so the eventual persistence/messaging adapters have an unambiguous, narrow contract to implement.

**Non-Goals:**
- Any adapter implementation (REST, JPA, Valkey client, Kafka client) — see proposal's Non-goals.
- Proving the lock-ordering/deadlock-freedom claim against a real database — that requires Testcontainers and belongs to the persistence change.
- Compensate handlers (ADR-0035).

## Decisions

### D1 — Transaction boundary: `UnitOfWork` outbound port, not `@Transactional`

`core/application/service` defines `UnitOfWork { <T> Result<T> inTransaction(Supplier<Result<T>> work) }` in `ports.outbound`. The handler calls it around exactly the atomic unit (§ below); a `Failure` result triggers rollback by contract. The persistence adapter implements it later with `TransactionTemplate`.

**Alternatives considered:**
- `@Transactional` directly on the handler method — rejected: it would wrap the idempotency gate call too (the gate must run *before* the transaction per ADR-0021/0026's "no external I/O inside the transaction, keep it short"), and self-invocation through a Spring proxy makes the real boundary invisible in the handler's own code, defeating ADR-0018's point.
- `@Transactional` on a second, inner bean the handler delegates to — rejected: splits one use case across two classes and hides the boundary behind a proxy; harder to review than a single method with an explicit port call.
- `UnitOfWork` (chosen): the boundary is one literal line of handler code, exactly matching ADR-0018's intent, with no proxy involved.

### D2 — Spring is allowed in `core/application`, not in `core/domain`

`@Service`/`@Component` and constructor injection are permitted in `core/application/{service,query}`. Spring Data, `ApplicationEventPublisher`, `@Value`/`Environment` lookups, and any annotation-processing dependency (Lombok, MapStruct — ADR-0031) remain banned there, and all of Spring remains banned in `core/domain`.

**Alternatives considered:**
- Keep `core/application` fully Spring-free, wire everything as `@Bean` in `container` — rejected: pushes every handler's dependency list into a separate configuration class per handler, adding indirection without a corresponding benefit once D1 already keeps persistence/transaction concerns out.
- No restriction at all ("full Spring freedom") — rejected: would let Spring Data repository types or the event publisher leak into use-case signatures, reintroducing exactly the coupling ADR-0006 forbids.

Consequence: `LayerBoundariesTest.core_does_not_depend_on_spring` narrows from `..core..` to `..core.domain..`; a new rule constrains `..core.application..` to `org.springframework.stereotype..` and `org.springframework.beans.factory..` only.

### D3 — `BalanceService` facade alongside per-use-case ports

CQRS (ADR-0007) wants one inbound port per use case; the challenge mandates one `BalanceService` interface with `credit`/`debit`/`transfer`/`getBalance`. Both are satisfied: `CreditUseCase`, `DebitUseCase`, `TransferUseCase`, `GetBalanceUseCase` are the real per-use-case contracts; `BalanceService` is a thin facade in `ports.inbound` that delegates to them and is the one place a `Result` failure becomes a thrown `BalanceOperationException` (forced by the challenge's `void` return type).

**Alternatives considered:**
- Per-use-case ports only, no `BalanceService` — rejected: the challenge explicitly requires this interface to exist; a reviewer checking the literal contract would not find it.
- `BalanceService` as the *only* inbound port — rejected: bundles the read side into the write-side contract, violating ADR-0007/0033 ("queries do not pass through application service").

### D4 — `occurredAt` from an injected `Clock`, never in the command

Each handler takes a `java.time.Clock` constructor dependency and resolves `Instant at = clock.instant()` once per invocation. Commands carry no timestamp field.

**Alternatives considered:**
- `occurredAt` explicit on the command — rejected: lets an untrusted client set ledger time, and every retry would need to resend an identical value for idempotency to make sense.
- Optional command field with `Clock` fallback — rejected: two sources of truth for ledger time is strictly worse than one, for no real flexibility gain at this stage.

### D5 — Scope excludes compensate

ADR-0035 already defers compensate's HTTP exposure to its own change; this design keeps every port and handler shape open to a later `compensate*` addition (e.g., `TransactionRepository` already needs no changes to support it) without building it now.

### D6 — Railway-oriented composition over `Result`

Handlers are `Result` chains (`flatMap`), not imperative `if`-chains, matching ADR-0005/0011's "results, not exceptions" and the domain's own composition style. Two exceptions to a pure two-track railway, both deliberate:
- **Parsing/validation collapses into one factory** (`TransferIntent.from(command, validator)`), producing a single `Result<TransferIntent>` instead of four nested `flatMap`s over primitive conversions.
- **The idempotency-gate outcome is not a `Result` failure** — a replay is a *success* with different provenance, which a two-track railway cannot express. It is handled once per handler via an exhaustive `switch` over the sealed `GateDecision`, which is a typed branch, not the scattered `if` ADR-0011 warns against.

No new `Result` combinators are added speculatively; any handler that needs one gets it with its own test, when it needs it.

### D7 — Postgres authoritative, Valkey advisory and fail-open

ADR-0021 leaves a crash window open: a process can die between the Valkey `SETNX` and the DB commit, leaving an orphan `IN_PROGRESS` key whose meaning a *later* reader must interpret. This design removes that interpretation problem by construction: `IdempotencyGate.tryBegin` returns a sealed `GateDecision` of exactly `Won`, `AlreadyApplied(outcome)`, or `Unknown` — there is no `IN_PROGRESS` case to read. `Unknown` (including "Valkey unreachable") always falls through to the authoritative DB path in step 5 below. Valkey answers `AlreadyApplied` only once it has seen a genuinely completed outcome; every other state — including its own absence — degrades to `Unknown` and costs latency, never correctness.

**Alternatives considered:**
- Implement ADR-0021's `IN_PROGRESS`/TTL/`DEL`-on-failure protocol as written — rejected: it works, but leaves a documented "trade-off" (its own words) that this design can remove entirely for the same amount of code, by making the DB path unconditionally authoritative instead of conditionally trusted.
- Trust Valkey `AlreadyApplied` without ever re-checking Postgres — rejected: reintroduces the original crash window.

### D8 — Duplicate check runs after the row locks, inside the transaction

`ProcessedTransactionStore.find(txId)` is called only after `AccountRepository.lockAllInIdOrder(ids)` succeeds, inside the same `UnitOfWork.inTransaction` block. Two concurrent duplicates therefore serialize on the row lock; the loser's own transaction re-reads and sees the winner's committed record, and replays without ever hitting the DB unique-constraint error path on the common case.

**Alternatives considered:**
- Check `ProcessedTransactionStore` before taking any lock — rejected: under concurrency, two duplicates can both pass the pre-lock check before either commits (classic TOCTOU), pushing correctness onto the UNIQUE constraint and a subsequent error-handling/retry path for what should be the common case.

### D9 — `UnitOfWork.inTransaction` is `Result`-typed with `Failure ⇒ rollback`

`<T> Result<T> inTransaction(Supplier<Result<T>> work)`. This keeps the railway (D6) intact across the transaction boundary and gives rollback an explicit, testable contract rather than an implicit "exception rolls back" convention that a `Result`-based codebase doesn't otherwise rely on.

### D10 — Read DTOs carry domain value objects, not primitives

`BalanceView` holds `AccountId` and `Money`, not `String`/`long`. `Money` is exact and unbounded (ADR-0036); flattening to `long` inside the port would silently reintroduce the overflow ADR-0036 explicitly rejected. The one place `Money` narrows to `long` is the `BalanceService` facade, at the challenge's mandated boundary, with the narrowing behavior documented.

### Resolving the ADR-0034 validation overlap

ADR-0034 requires both jakarta annotations on the command *and* a `Validator` call in the handler; the domain's own factories (`Money.operationAmount`, `TransactionId.of`, the new `AccountId.of`) already enforce the same rules and return the correct `ErrorCode`. Resolution: the `Validator` runs first, for structural shape only (`@NotBlank`, `@Positive` — presence/format), via a small `CommandValidator` helper that maps `ConstraintViolation` property paths to `ErrorCode`s (`amount → INVALID_AMOUNT`, `transactionId → INVALID_TRANSACTION_ID`, `*accountId → INVALID_ACCOUNT_ID`). The intent factory then produces typed domain values through the domain's own validating factories. The overlap on `@Positive`/`Money.operationAmount` is defence in depth — one value can only be rejected once in practice — not two competing sources of truth, since both paths agree on the code.

## The atomic unit, in detail

Per ADR-0018/0026/0037/0010, the atomic unit is **N accounts + 1 `Transaction` (+ its `LedgerEntry` list) + 1 processed-transaction record + outbox rows**, committed in one DB transaction:

```
handle(command):
  1. validate(command)                 jakarta Validator, mapped to ErrorCode      — outside tx
  2. build intent                      String→AccountId, long→Money, →TransactionId
  3. domain pre-flight spec            same-account transfer (ADR-0028)            — outside tx, txId unconsumed
  4. gate.tryBegin(txId)                                                          — outside tx
        AlreadyApplied(outcome) -> return outcome as REPLAYED
        Won | Unknown           -> continue
  5. unitOfWork.inTransaction:                                                    <-- THE BOUNDARY
       a. accounts.lockAllInIdOrder(ids)     FOR UPDATE, ascending AccountId (ADR-0026)
       b. processed.find(txId)               authoritative check, AFTER the locks
                                             present -> REPLAYED, nothing mutated
       c. postingService.credit|debit|transfer(..., clock.instant())
       d. accounts.saveAll · transactions.save · processed.record(txId, outcome)
       e. outbox.append(pullDomainEvents() from every mutated aggregate)
  6. after commit: gate.publishOutcome(txId, outcome)     — best effort
     on failure:   gate.release(txId)
```

**Crash / failure matrix** (this is what makes D7 correct, not merely simpler):

| Failure point | Residual state | Next retry's behavior |
|---|---|---|
| After `tryBegin` returns `Won`, before commit | Valkey key set, DB untouched | Not `AlreadyApplied` ⇒ `Unknown` ⇒ locks, finds nothing, applies. Correct |
| After commit, before `publishOutcome` | DB record exists, Valkey stale/unset | `Unknown` ⇒ locks, finds record ⇒ `REPLAYED`. Correct |
| Valkey down or flushed entirely | No gate at all | Every request is `Unknown` ⇒ DB path every time. Correct, only slower |
| Two concurrent requests, both `Unknown` | — | Serialize on the account row locks; the loser replays |
| Same `transactionId`, different accounts/amount on retry | Different lock set, step 5b misses | `processed_transaction` UNIQUE constraint rejects at commit ⇒ surfaced as a conflict, a documented client error, never silent acceptance |

No external I/O happens inside the transaction (ADR-0026): the Valkey call is strictly before it, and Kafka publication is a separate outbox poller, not part of this change.

## Deadlock analysis

Both account rows a transfer touches are locked inside step 5a, in one order determined solely by `AccountId`'s canonical comparator (a byte-wise unsigned comparison matching PostgreSQL's native `uuid` ordering — see the `balance-domain-model` delta spec). Because every transfer — regardless of which account is named source and which destination — takes its locks in that same order, the wait-for graph between concurrent transfers can never contain a cycle: deadlock is impossible by construction, not by timeout or retry. Credit/debit lock exactly one row, so no ordering question arises for them. The `AccountRepository.lockAllInIdOrder` port receives an *unordered* collection and owns the ordering itself, so no caller can defeat the guarantee by passing ids in the "wrong" order.

## Risks / Trade-offs

**[Risk] A hot single account serializes all operations against it, bounding its throughput to lock-acquisition speed.** → Mitigation: this is ADR-0026's explicit, accepted trade-off for correctness; independent accounts are unaffected (no global lock, no clearing account per ADR-0037), so aggregate throughput scales with account spread, which is the realistic traffic shape for a balance service.

**[Risk] `synchronized` or any blocking call around I/O inside a handler would pin the carrier thread under virtual threads (ADR-0002), silently degrading concurrency.** → Mitigation: `UnitOfWork`/repository ports are the only blocking calls, and none of the application-layer code introduces `synchronized`; enforced by code review and, if warranted, an ArchUnit rule in a later change.

**[Risk] Unbounded virtual threads in front of a fixed JDBC pool can turn into an invisible queue under load.** → Mitigation: pool sizing is a container/persistence-change concern, out of scope here; flagged in the proposal's Impact section so it isn't rediscovered under load without context.

**[Risk] This change's concurrency and idempotency tests run against in-memory fakes, not a real database.** → Mitigation: explicitly named as the honest gap in `proposal.md`'s Non-goals and carried into `tasks.md`; the fakes model row-level locking with a per-account `ReentrantLock` acquired in the same canonical order the real port promises, which is sufficient to prove handler *logic* (lock-then-check ordering, gate short-circuiting, atomic-unit composition) but not the database's own lock semantics. The real proof requires Testcontainers and lands with the persistence change.

**[Risk] `getBalance`'s narrowing from `Money` (unbounded) to `long` (the challenge's mandated return type) can overflow.** → Mitigation: the facade uses `longValueExact()`, so overflow throws rather than silently truncating or wrapping; documented in the facade's Javadoc and the README rather than hidden.

## Migration Plan

Purely additive: new modules' code, no schema, no runtime behavior change to any existing deployed component (there is none yet). Rollback is `git revert` of the merge commit; no data migration exists to reverse. `./mvnw test` must stay green and Docker-free throughout, including the ArchUnit narrowing.
