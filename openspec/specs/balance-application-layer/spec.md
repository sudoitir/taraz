# balance-application-layer Specification

## Purpose

The application boundary that turns the pure-Java balance domain into a callable service: commands and their handlers on the write side, queries on the read side, and the ports both sides need — guaranteeing atomicity, idempotency, and input validation at exactly one point before any adapter exists.

## Requirements

### Requirement: Commands are the only write-side input

Every write-side use case SHALL accept only an immutable command record defined in the application layer's inbound ports; no web DTO, persistence entity, or other transport-specific type SHALL cross into a command handler.

#### Scenario: Handler signature accepts only the command type

- **WHEN** a credit, debit, or transfer use case is invoked
- **THEN** its only parameter is the corresponding command record, and no framework or transport type appears in the call

### Requirement: Amount and identifier validation

Every command SHALL be validated before any account is loaded or any lock is taken: a non-positive amount, a blank or malformed account identifier, or a blank transaction identifier SHALL each fail with a distinct, stable error code and leave every balance unchanged.

#### Scenario: Non-positive amount rejected

- **WHEN** a credit, debit, or transfer command carries `amount <= 0`
- **THEN** the operation fails with error code `INVALID_AMOUNT`, no account is loaded, and no balance changes

#### Scenario: Blank transaction id rejected

- **WHEN** a command carries a blank or missing `transactionId`
- **THEN** the operation fails with error code `INVALID_TRANSACTION_ID` before any account lookup

#### Scenario: Malformed account id rejected

- **WHEN** a command carries an account identifier that is not a valid identifier
- **THEN** the operation fails with error code `INVALID_ACCOUNT_ID` before any account lookup

### Requirement: Unknown account is rejected with a distinct error

A credit, debit, or transfer referencing an account identifier that does not correspond to any existing account SHALL fail without mutating any balance.

#### Scenario: Credit or debit on an unknown account

- **WHEN** a credit or debit command references an `accountId` with no corresponding account
- **THEN** the operation fails with error code `ACCOUNT_NOT_FOUND` and no balance changes

#### Scenario: Transfer referencing an unknown account

- **WHEN** a transfer command references a source or destination `accountId` with no corresponding account
- **THEN** the operation fails with error code `ACCOUNT_NOT_FOUND`, and both the source and destination balances (where they exist) are unchanged

### Requirement: Same-account transfer rejected before any effect

A transfer whose source and destination are the same account SHALL be rejected before any lock is acquired or any transaction is opened, and its `transactionId` SHALL NOT be consumed.

#### Scenario: Self-transfer rejected pre-lock

- **WHEN** `transfer(A, A, 100, "TX-1")` is requested
- **THEN** the operation fails with error code `SAME_ACCOUNT_TRANSFER`, no lock is acquired, no transaction is opened, and A's balance is unchanged

#### Scenario: Transaction id remains usable after a self-transfer rejection

- **WHEN** `transfer(A, A, 100, "TX-1")` is rejected, and afterward a valid operation is submitted with the same `transactionId` value
- **THEN** the later operation is evaluated on its own merits, not treated as a duplicate of the rejected request

### Requirement: A transaction id affects balances exactly once

Repeated submission of a command carrying the same `transactionId` — sequentially or concurrently — SHALL change the affected balance or balances at most once; every repeat SHALL report the outcome of the original application without a second effect.

#### Scenario: Sequential duplicate credit

- **WHEN** `credit(A, 100, "TX-1")` is submitted three times in sequence, with A starting at 1000
- **THEN** A's final balance is 1100, and the second and third calls report the same outcome as the first without changing the balance further

#### Scenario: Sequential duplicate debit

- **WHEN** `debit(A, 100, "TX-1")` is submitted three times in sequence, with A starting at 1000 and sufficient funds throughout
- **THEN** A's final balance is 900, and the second and third calls report the same outcome as the first without a second deduction

#### Scenario: Sequential duplicate transfer

- **WHEN** `transfer(A, B, 100, "TX-1")` is submitted three times in sequence
- **THEN** A and B each reflect exactly one 100-unit movement, and the second and third calls report the same outcome as the first

#### Scenario: Concurrent duplicates

- **WHEN** N concurrent requests carry credit, debit, or transfer commands with the same `transactionId` and the same operation parameters
- **THEN** exactly one request causes a balance change and the remaining N−1 report the outcome of that one application

### Requirement: Idempotency holds even when the fast-path gate is unavailable

The exactly-once guarantee on `transactionId` SHALL hold even when any advisory, non-authoritative idempotency-acceleration mechanism is unreachable or returns no answer.

#### Scenario: Duplicate survives fast-path gate outage

- **WHEN** the same `transactionId` is submitted twice while the advisory gate reports no prior knowledge of it both times
- **THEN** at most one of the two submissions changes any balance

### Requirement: The atomic unit commits or fails as one

For a single command, the resulting account balance change(s), the recorded transaction, the durable record marking the `transactionId` processed, and the events describing the change SHALL commit together or none of them SHALL take effect.

#### Scenario: Successful application commits everything together

- **WHEN** a credit, debit, or transfer command succeeds
- **THEN** the affected account balance(s), the recorded transaction, the processed-transaction record, and the recorded events are all durably visible together

#### Scenario: A failure partway through commits nothing

- **WHEN** any step of applying a command fails after an account has been loaded but before the atomic unit completes
- **THEN** no account balance changes, no transaction is recorded, no `transactionId` is marked processed, and no event is recorded

### Requirement: Insufficient funds executes nothing

A debit or transfer whose source account cannot cover the requested amount SHALL leave every involved balance unchanged and SHALL NOT record a transaction, a processed-transaction entry, or an event.

#### Scenario: Debit beyond balance

- **WHEN** a debit of 700 is requested on an account with balance 500
- **THEN** the operation fails with error code `INSUFFICIENT_FUNDS`, the balance is still 500, and no transaction, processed record, or event is produced

#### Scenario: Transfer beyond source balance

- **WHEN** a transfer of 700 is requested from a source account with balance 500
- **THEN** the operation fails with error code `INSUFFICIENT_FUNDS`, and both the source and destination balances are unchanged

### Requirement: Account row locks are taken in one canonical order

When a transfer must lock two account rows, the order in which they are locked SHALL depend only on the identities of the two accounts, not on which was named source or destination, so that no two concurrent transfers between the same pair of accounts can wait on each other in opposite orders.

#### Scenario: Order is independent of transfer direction

- **WHEN** a transfer from account X to account Y and a separate transfer from account Y to account X are both submitted
- **THEN** both operations acquire the lock on the same one of the two accounts first, regardless of which is source and which is destination

#### Scenario: Concurrent opposite-direction transfers complete without deadlock

- **WHEN** many concurrent transfers alternate direction between the same two accounts
- **THEN** every transfer eventually completes (success or a predicted failure), and none is abandoned due to a lock cycle

### Requirement: Independent accounts are never blocked by unrelated operations

An operation affecting one set of accounts SHALL NOT be blocked by a concurrent operation affecting a disjoint set of accounts.

#### Scenario: Unrelated accounts proceed independently

- **WHEN** concurrent operations target two disjoint sets of accounts
- **THEN** neither set's operations wait on the other set's lock

### Requirement: Recorded events are delivered to the outbox exactly once per occurrence

Every event recorded by a successful operation SHALL be appended to the outbox exactly once as part of the same atomic unit as the balance change it describes.

#### Scenario: Events accompany their balance change

- **WHEN** a credit, debit, or transfer succeeds
- **THEN** every event it records is present in the outbox once the atomic unit is visible, and not before

#### Scenario: A failed operation produces no event

- **WHEN** an operation fails any validation or business rule
- **THEN** no event for that attempt appears in the outbox

### Requirement: The read side never mutates state

Retrieving an account's balance SHALL NOT open a transaction, acquire a lock, change any balance, or record any event.

#### Scenario: Balance read is side-effect free

- **WHEN** a balance is retrieved for an existing account
- **THEN** the account's balance, recorded events, and any other observable state are unchanged by the read

#### Scenario: Balance read on an unknown account

- **WHEN** a balance is retrieved for an account identifier with no corresponding account
- **THEN** the read fails with error code `ACCOUNT_NOT_FOUND` and no state is created or changed
