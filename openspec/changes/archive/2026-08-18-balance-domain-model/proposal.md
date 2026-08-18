# Proposal: balance-domain-model

## Why

`core/domain` is an empty module today: one `package-info.java`, zero types. Every ADR from 0005 onward
describes a domain that does not exist yet, and the challenge is graded on exactly what lives here —
"no negative balance", "no partial operation", "amount deducted equals amount credited". This change
builds the domain layer end to end as a pure Java module with its own unit tests, so the concurrency
and persistence changes that follow are tested against a domain already provably correct on its own.

## What Changes

- New building blocks in `core/domain/common`: `Result<T>` (sealed success/failure), `DomainError`,
  `ErrorCode` catalog, `AbstractEntity`/`AbstractAggregateRoot` (identity equality + domain-event
  recording), `DomainEvent`/`AbstractDomainEvent`, Fowler-style `Specification<T>` with a
  `Result`-returning `check()` and and/or/not combinators, domain-owned `IdGenerator` with a JUG-backed
  `UuidV7IdGenerator` (ADR-0016).
- New `money.Money` — `BigDecimal` minor units (whole numbers only), single implicit currency, exact
  arithmetic with no overflow ceiling (ADR-0036, new).
- New `account` aggregate: `Account` (id + balance, minimal), `AccountId`, positive-amount and
  sufficient-funds specifications, `AccountOpened`/`AccountCredited`/`AccountDebited` events.
- New `transaction` aggregate: immutable `Transaction` owning double-entry `LedgerEntry` legs,
  `TransactionId` (client-supplied `String`), `EntryId`, type/status/direction enums, leg-shape
  specifications (uniform amount, entries-match-type, distinct transfer accounts per ADR-0028),
  `TransactionPosted`/`TransactionCompensated` events, and `compensationOf` reversal (ADR-0035,
  ADR-0037 new).
- New `service.PostingService` — the domain's single entry point for credit/debit/transfer/compensate;
  evaluates every specification before any aggregate is mutated, so "no partial operation" holds by
  construction. Returns `PostingResult(transaction, mutatedAccounts)`.
- Failure contract per ADR-0005/0011: predicted domain failures are `Result.Failure(DomainError)`,
  never exceptions; programmer errors throw unchecked exceptions from builders.
- New ArchUnit rules in `architecture-tests` enforcing domain purity (no framework/ORM/transport, no
  ambient time or random UUIDs, builders only, no public setters).
- New ADRs 0036 (Money as BigDecimal minor units), 0037 (Transaction + LedgerEntry double-entry,
  zero-sum scoped to TRANSFER, clearing account rejected), 0038 (JUG for UUIDv7, virtual-thread pinning
  analysis) — Persian, per `.claude/rules/adr.md`.
- Root `pom.xml`: `jug.version` property + dependencyManagement entry; `core/domain/pom.xml`: JUG
  dependency, JUnit 5 + AssertJ (test scope), description reworded to "zero framework dependencies".

## Capabilities

### New Capabilities

- `balance-domain-model`: In-memory domain model for balance operations — Account and Transaction
  aggregates, Money arithmetic, specification-evaluated business rules, domain events, and the
  PostingService seam — guaranteeing the challenge invariants (no negative balance, no partial
  operation, atomic transfer, defined same-account-transfer behavior) structurally, before any
  concurrency or persistence concerns.

### Modified Capabilities

(none)

## Non-goals

- **No idempotency logic.** `transactionId` uniqueness lives in the command handler, Valkey and the DB
  unique constraint (ADR-0021/0034). The domain treats `TransactionId` as an opaque correlation value.
- **No locking, no `@Version`, no transaction management** (ADR-0026/0018) — those belong to the
  application and persistence layers.
- **No repositories, ports, JPA entities, mappers, REST, outbox, Kafka, `IntegrationEvent`.**
- **No Lombok, no MapStruct, no Spring in `core`** (ADR-0031).
- **No account lifecycle/status, no owner, no currency, no multi-currency.**
- No Testcontainers, no k6 — this module has no infrastructure to integrate with.

## Impact

- **Code**: all new code under `core/domain/src/main/java/io/github/sudoitir/taraz/core/domain/{common,money,account,transaction,service}` plus mirrored unit tests; new ArchUnit rules in
  `architecture-tests/.../LayerBoundariesTest.java`.
- **Dependencies**: first compile dependency in `core/domain` — `com.fasterxml.uuid:java-uuid-generator`
  (JUG), pinned in the root `pom.xml` (ADR-0038).
- **ADRs**: new 0036, 0037, 0038 in `docs/adr/`.
- **References**: ADR-0005 (domain purity, failure contract), ADR-0009/0010 (event model, outbox seam),
  ADR-0011 (specifications + Result), ADR-0016 (UUIDv7), ADR-0026 (pessimistic locking — informs the
  no-global-clearing-account decision), ADR-0028 (same-account transfer rejection), ADR-0033 (module
  hierarchy), ADR-0035 (compensation); challenge rules `challenge-consistency`, `challenge-transfer`,
  `challenge-testing` (domain-level invariant tests).
