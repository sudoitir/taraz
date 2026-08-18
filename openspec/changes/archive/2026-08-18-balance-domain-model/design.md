# Design: balance-domain-model

## Context

See `proposal.md` — Why. `core/domain` is an empty module; this change fills it with a pure Java domain
(JDK + JSpecify + JUG only). Concurrency control, persistence and idempotency are deliberately **not**
here: per ADR-0026/0034 they live in the application and persistence layers. The domain's job is to
make an invalid balance state unrepresentable **in memory**; the outer layers make it unrepresentable
**under concurrency**.

**Settled scope decisions** (agreed with the challenge owner): `Transaction` + `LedgerEntry`
double-entry as the second aggregate; `Money` as `BigDecimal` minor units with no currency (owner
decision during apply, superseding the earlier `long` sketch); `Account` minimal (id + balance, no
lifecycle); this change ships `core/domain` only — no ports, no application service.

## Goals / Non-Goals

**Goals:**

- Every challenge invariant (no negative balance, no partial operation, exact transfer equality,
  defined same-account-transfer behavior) enforced structurally by types and builders, not by caller
  discipline.
- A single domain seam (`PostingService`) that the future application handler reduces to: lock rows in
  `accountId` order → load accounts → call `PostingService` → persist in one transaction → publish
  pulled events.
- Deterministic, dependency-free core verifiable by fast unit tests alone.

**Non-Goals:** see `proposal.md` — Non-goals (no idempotency, no locking, no persistence, no
frameworks). Design-level addition: no thread-safety machinery inside the domain (see Concurrency
posture below).

## Decisions

### D1. Failure contract — two channels, never mixed (ADR-0005, ADR-0011)

- **Predicted domain failure → `Result.Failure(DomainError)`.** Insufficient funds, non-positive
  amount, same-account transfer, overflow, unbalanced legs. Never an exception — these are ordinary
  domain outcomes.
- **Programmer error → unchecked exception.** A missing required builder field is a bug, not a
  business outcome; JSpecify + NullAway catch most at compile time, `build()` throws
  `IllegalStateException` for the rest.

`Result<T>` is a sealed interface (`Success<T> | Failure<T>`) with `map`/`flatMap` so `PostingService`
stays linear instead of a staircase of `if (result.isFailure())`. `ErrorCode` is a stable enum catalog
(`INVALID_AMOUNT`, `INSUFFICIENT_FUNDS`, `NEGATIVE_BALANCE`,
`SAME_ACCOUNT_TRANSFER`, `UNBALANCED_TRANSACTION`, `INVALID_ENTRY_SHAPE`, `INVALID_TRANSACTION_ID`,
`COMPENSATION_TARGET_NOT_APPLIED`); tests assert on codes, never messages.

### D2. Specification — Fowler's predicate with ADR-0011's Result edge

`Specification<T>` exposes `isSatisfiedBy`, `violation` (the `DomainError` to report), `check`
(returning `Result<T>`), and `and`/`or`/`not` combinators on `AbstractSpecification`. `and()` is
fail-fast and **left-biased** — the caller receives the first, most specific violation (amount validity
before funds sufficiency). `not()` requires the `DomainError` explicitly; a negated rule cannot infer a
readable failure. Composites (`AndSpecification`/`OrSpecification`/`NotSpecification`) are
package-private so the set of shapes stays closed and testable.

### D3. Uniform builder pattern — constructor takes the builder

`Account`, `Transaction`, `LedgerEntry` and every event: private constructor accepting the builder;
`builder()` the only entry point. For **aggregates** (`Account`, `Transaction`), `build()` returns
`Result<T>` after `requireAllFieldsPresent()` (programmer error → `IllegalStateException`) and
specification checks (domain rule → `Result`). Child entities and events (`LedgerEntry`, concrete events)
have no domain failure modes of their own — their only failures are missing required fields — so their
builders return the object directly and throw on programmer error. No public no-arg constructor, no
setter anywhere — the only route to a domain object is a builder that has already validated it, which is
precisely ADR-0005's "دورزدن invariantها با constructor خالی ممنوع است".

### D4. Identity and aggregate bases

`AbstractEntity<ID>`: final `getClass()`-based `equals` (not `instanceof` — entities are subclassable
and `instanceof` breaks symmetry), `hashCode` from the immutable id only. `AbstractAggregateRoot<ID>`
adds domain-event recording: `registerEvent`, `domainEvents()` (unmodifiable view),
`pullDomainEvents()` (copy-then-clear, so a second call cannot re-publish). Plain `ArrayList` — see
Concurrency posture.

### D5. Domain events — no `eventId` in the domain

`DomainEvent` carries `eventType()`, `occurredAt()`, `transactionId()`. The unique publication id
belongs to the outbox row and the `IntegrationEvent` built in the driven adapter (ADR-0009/0010);
putting it here would leak a publication concern into the domain. Events are created **only** through
`AccountEvents` / `TransactionEvents` factories (ADR-0005), and carry the resulting balance where
relevant so a consumer never reconstructs it.

`transactionId()` is a `@Nullable String`, not the `TransactionId` record: `TransactionId` lives in the
`transaction` package, and referencing it from `common` would close a package cycle (transaction → common
→ transaction). The raw client correlation string keeps `common` dependency-free; it is `null` only for
events not caused by a transaction (`AccountOpened`).

### D6. `Money` — `BigDecimal` minor units, single implicit currency (ADR-0036)

`record Money(BigDecimal minorUnits)`, normalized in the compact constructor (`stripTrailingZeros()`)
so equality is scale-blind. Values are always whole minor units: factories reject fractional input with
`INVALID_AMOUNT` (the API boundary supplies `long`, so fractions have no meaning). `add` is exact and
unbounded — no overflow failure mode exists, so the catalog has no `BALANCE_OVERFLOW`; the only
arithmetic failure is `minus` below zero → `INSUFFICIENT_FUNDS`. The API boundary stays the challenge's
`long amount`; conversion happens at the application edge. Rejected: `long` + `Math.addExact`
(artificial `Long.MAX_VALUE` ceiling and a non-business failure mode), `Money(amount, currency)` (a
field nothing in the API supplies).

### D7. `Account` — minimal aggregate

`open(id, initialBalance, at)` emits `AccountOpened`; `reconstitute(id, balance)` emits nothing
(replaying `AccountOpened` on every load would flood the outbox). Mutators take a resolved `Instant`,
not a `Clock` — the domain stays deterministic with zero ambient state (ADR-0005); the `Clock` lives in
the application layer. `debit` evaluates
`PositiveAmountSpecification.and(SufficientFundsSpecification)` **before** touching the field, so a
rejected debit leaves the balance byte-identical.

### D8. `Transaction` + `LedgerEntry` — double-entry, zero-sum scoped to TRANSFER (ADR-0037)

Fully immutable once built; holds an unmodifiable `List<LedgerEntry>`. Leg shape by type:

| Type | Legs | Zero-sum? |
| --- | --- | --- |
| `TRANSFER` | exactly 2 — one `DEBIT`, one `CREDIT`, distinct accounts, equal amounts | yes, enforced |
| `CREDIT` | exactly 1 `CREDIT` leg | n/a — money enters from outside the service |
| `DEBIT` | exactly 1 `DEBIT` leg | n/a — money leaves the service |

The textbook alternative — an internal clearing account so every transaction balances — is **rejected**:
that row becomes the counterparty of every credit and debit, and under ADR-0026's pessimistic row locks
it serializes all otherwise-independent accounts, violating the challenge's "independent accounts must
not block each other without a valid reason". Recorded in ADR-0037.

Invariants are specifications evaluated in `Transaction.Builder.build()`:
`PositiveAmountSpecification`, `UniformAmountSpecification`, `EntriesMatchTypeSpecification`,
`DistinctTransferAccountsSpecification` (ADR-0028).

`compensationOf(original, newId, at, ids)` reverses every leg's direction, sets `compensates`, and takes
the **reversed type** (credit↔debit per ADR-0035's mapping; transfers stay TRANSFER) so the leg-shape
specification still holds for the compensation. **One implementation covers all three ADR-0035 cases**
and is structurally incapable of producing an unbalanced reversal. Compensating anything not `APPLIED`
returns `COMPENSATION_TARGET_NOT_APPLIED`. The compensation is a new transaction with its own id; the
original is untouched (ADR-0035's audit rule).

### D9. `PostingService` — evaluate everything, then mutate

Stateless (only an `IdGenerator` field). `credit`/`debit`/`transfer`/`compensate` return
`Result<PostingResult>` where `PostingResult = (Transaction, List<Account> mutatedAccounts)`. **Every
specification is evaluated before any aggregate is mutated**, so "no partial operation" holds by
construction with no rollback logic in the domain.

### D10. Concurrency posture

Aggregates are **not** thread-safe and must not be. Each command loads a fresh instance under a row
lock and discards it at commit; instances are never shared across threads. `AbstractAggregateRoot` uses
a plain `ArrayList` — a concurrent collection would falsely advertise shared mutability and obscure
where the real concurrency control lives (ADR-0026). The one shared object is `UuidV7IdGenerator`,
whose JUG generator is documented thread-safe; JUG synchronizes internally on generation, which under
virtual threads pins the carrier for a few nanoseconds of pure in-memory work — not the blocking-pinning
hazard ADR-0002 warns about (analysis in ADR-0038).

**Transaction boundaries (ADR-0018): none here.** The domain opens no transactions; the boundary is the
future application handler (lock rows in `accountId` order → `PostingService` → persist accounts +
transaction + outbox row in one DB transaction → `pullDomainEvents()` publish). This design only
guarantees the handler needs no domain-internal compensation logic.

**Deadlock analysis (transfer):** the domain itself acquires no locks, so it cannot deadlock.
Deadlock freedom for transfer is a property of the persistence layer's consistent lock ordering by
`accountId` (ADR-0026); the domain supports it by keeping both accounts' mutation inside one
side-effect-free evaluation so the handler can persist them atomically in either lock order.

### D11. UUIDv7 via JUG behind a domain-owned interface (ADR-0016, ADR-0038)

`com.fasterxml.uuid:java-uuid-generator` — `Generators.timeBasedEpochGenerator()` (RFC 9562 v7),
wrapped behind the domain's own `IdGenerator` so the domain never imports the library outside one class
(ADR-0005's "external need = local interface"). Rejected: hand-rolled v7 (RFC edge cases, monotonicity
within a millisecond), `uuid-creator`, waiting for JDK support. Version pinned as `jug.version` +
dependencyManagement in the root `pom.xml`; confirm the current 5.x release on Maven Central at apply
time. This is the first compile dependency in `core/domain`; its pom description changes from "zero
dependencies" to "zero framework dependencies", matching ADR-0005's actual wording.

## Risks / Trade-offs

- [Double-entry adds a second aggregate where a flat balance delta would do] → auditability and the
  zero-sum transfer invariant are graded challenge concerns; the cost is one child entity, accepted and
  recorded in ADR-0037.
- [`BigDecimal` money costs allocation vs `long` and cannot represent currencies] → exact, unbounded
  arithmetic removes the overflow failure class entirely; allocation is negligible at challenge volume.
  ADR-0036 records the trade-off.
- [Builders returning `Result<T>` are unconventional] → keeps "no invalid object exists" structural;
  uniformity across all four built types keeps the surprise one-time.
- [JUG's internal synchronization under virtual threads] → bounded nanosecond critical section, no
  blocking call inside; measured risk accepted in ADR-0038, `IdGenerator` seam allows swapping without
  touching the domain.
