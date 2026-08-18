# Spec Delta: balance-domain-model

## Purpose

In-memory domain model for balance operations — accounts, money arithmetic, double-entry transactions, business rules, and domain events — that makes the challenge invariants (no negative balance, no partial operation, atomic transfer) unrepresentable in memory, before any concurrency or persistence concern is applied.

## ADDED Requirements

### Requirement: Amount validation

Every balance-affecting operation SHALL reject non-positive amounts without mutating any state.

#### Scenario: Zero or negative amount rejected

- **WHEN** a credit, debit, or transfer is requested with `amount <= 0`
- **THEN** the operation fails with error code `INVALID_AMOUNT` and no account balance changes and no domain event is recorded

#### Scenario: Positive amount accepted

- **WHEN** a credit, debit, or transfer is requested with `amount > 0` and all other rules satisfied
- **THEN** amount validation passes

### Requirement: Debit never produces a negative balance

A debit SHALL succeed only when the account balance covers the amount; otherwise the operation SHALL NOT be executed and the balance SHALL remain unchanged.

#### Scenario: Debit beyond balance

- **WHEN** a debit of 700 is requested on an account with balance 500
- **THEN** the operation fails with error code `INSUFFICIENT_FUNDS` and the balance is still 500 and no domain event is recorded

#### Scenario: Debit of exactly the balance

- **WHEN** a debit of 500 is requested on an account with balance 500
- **THEN** the operation succeeds and the resulting balance is 0

### Requirement: Exact, unbounded balances

Balances SHALL be exact decimal minor units with no fixed overflow ceiling; a credit SHALL never wrap, truncate, or cap the balance.

#### Scenario: Credit beyond the long range

- **WHEN** credits accumulate a balance beyond `Long.MAX_VALUE` minor units
- **THEN** the balance reflects the exact sum and no overflow error is raised

#### Scenario: Fractional minor units rejected

- **WHEN** a money amount with a fractional minor-unit value is supplied to the domain
- **THEN** it is rejected with error code `INVALID_AMOUNT`

### Requirement: Transfer atomicity in memory

A transfer SHALL move exactly the same amount from source to destination as one indivisible in-memory effect: the amount deducted from the source SHALL equal the amount added to the destination.

#### Scenario: Successful transfer

- **WHEN** a transfer of 300 from account A (balance 1000) to account B (balance 500) succeeds
- **THEN** A's balance is 700 and B's balance is 800, and the transaction records exactly two legs — one debit on A and one credit on B — of equal amount that net to zero

#### Scenario: Failed transfer leaves both accounts untouched

- **WHEN** a transfer fails any rule evaluation (insufficient source funds, invalid amount, same accounts)
- **THEN** both account objects are in a state identical to before the call, and no transaction is produced and no domain event is recorded

### Requirement: Same-account transfer rejected

A transfer whose source and destination are the same account SHALL be rejected before any state is evaluated or mutated (ADR-0028).

#### Scenario: Transfer from an account to itself

- **WHEN** `transfer(A, A, 100, txId)` is requested
- **THEN** the operation fails with error code `SAME_ACCOUNT_TRANSFER` and A's balance is unchanged

### Requirement: Transaction leg integrity

A transaction SHALL be constructible only with legs matching its type, and a transfer transaction SHALL always net to zero across its legs.

#### Scenario: Leg shape matches type

- **WHEN** a transaction is built whose leg count or directions do not match its declared type (one credit leg for CREDIT, one debit leg for DEBIT, one debit plus one credit leg for TRANSFER)
- **THEN** construction fails with error code `INVALID_ENTRY_SHAPE` or `UNBALANCED_TRANSACTION` and no transaction instance exists

#### Scenario: Uniform leg amounts

- **WHEN** a transaction is built whose legs carry different amount magnitudes
- **THEN** construction fails and no transaction instance exists

### Requirement: Compensation reverses an applied transaction

Compensating an applied transaction SHALL produce a new transaction with its own identifier, every leg's direction reversed, and a link back to the compensated transaction; the original transaction SHALL remain untouched (ADR-0035).

#### Scenario: Compensate an applied transaction

- **WHEN** compensation is requested for a transaction in status `APPLIED`
- **THEN** a new transaction is produced whose legs are the exact reversal of the original's legs, which references the original transaction, and which is itself balanced

#### Scenario: Compensate a non-applied transaction

- **WHEN** compensation is requested for a transaction that is not in status `APPLIED`
- **THEN** the operation fails with error code `COMPENSATION_TARGET_NOT_APPLIED`

### Requirement: No partial operation

Every business rule of an operation SHALL be evaluated before any aggregate is mutated, so a rejected operation can never leave a half-applied in-memory state.

#### Scenario: Rule failure precedes mutation

- **WHEN** any operation fails rule evaluation
- **THEN** every involved account's observable state (balance, recorded events) is identical to its pre-call state

### Requirement: Predicted failures are values, not exceptions

Predicted domain failures (invalid amount, insufficient funds, same-account transfer, unbalanced legs, invalid compensation target) SHALL be returned as typed failure values carrying a stable error code; they SHALL NOT be thrown as exceptions. Tests and callers SHALL be able to assert on the error code, never on a message string.

#### Scenario: Failure carries stable code

- **WHEN** any predicted domain failure occurs
- **THEN** the result is a failure value exposing one of the catalogued error codes, and the operation throws no exception

### Requirement: Domain events record applied changes

Successful state changes SHALL record domain events carrying the operation's transaction identifier, occurrence time, and — where relevant — the resulting balance, so a consumer never has to reconstruct it. Reconstitution of an aggregate from persisted state SHALL NOT record events.

#### Scenario: Events on success

- **WHEN** a credit, debit, transfer, or account opening succeeds
- **THEN** the corresponding domain events are recorded on the affected aggregates with the operation's transaction id and timestamp

#### Scenario: Reconstitution is silent

- **WHEN** an account is reconstituted from persisted state
- **THEN** no domain event is recorded

#### Scenario: Events are handed over exactly once

- **WHEN** recorded events are pulled from an aggregate at a transaction boundary
- **THEN** the caller receives all recorded events and a subsequent pull returns none

### Requirement: Deterministic domain

The domain SHALL contain no ambient state: timestamps and identifiers SHALL be supplied by the caller, and the domain SHALL NOT call system clocks or random UUID generators.

#### Scenario: Supplied time and ids

- **WHEN** any domain operation executes
- **THEN** all timestamps and generated identifiers it records come from parameters supplied by the caller, never from `System.currentTimeMillis`, `Instant.now()`, or `UUID.randomUUID()` inside the domain

### Requirement: Identity-based entity equality

Entities SHALL compare by identity and concrete class only, so equality is reflexive, symmetric, and transitive, and an entity's hash never changes while it sits in a collection.

#### Scenario: Equal ids, same class

- **WHEN** two entity instances of the same concrete class share the same id
- **THEN** they are equal and have equal hash codes

#### Scenario: Different concrete class

- **WHEN** two entity instances share an id but differ in concrete class
- **THEN** they are not equal

### Requirement: Domain purity

The domain module SHALL depend only on the JDK, JSpecify annotations, and the UUIDv7 generator library — no framework, ORM, transport, or code-generation dependency — and SHALL expose no public setters or public no-arg constructors that could bypass invariants.

#### Scenario: Forbidden dependency

- **WHEN** the architecture tests run
- **THEN** any dependency of domain classes on Spring, Jakarta, Lombok, MapStruct, or mutable-state shortcuts fails the build
