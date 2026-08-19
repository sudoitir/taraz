# persistence-adapter Specification

## Purpose

Implements the write-side repositories, the transaction boundary, the read-side balance query, and the
idempotency gate against real PostgreSQL and Valkey, replacing the in-memory test fakes with adapters
that uphold the same lock-ordering, atomicity, and fail-open guarantees under real infrastructure.

## Requirements

### Requirement: Account row locks are acquired in canonical order against real rows

When a command needs to lock one or more account rows, the adapter SHALL acquire a real database row
lock on each in ascending canonical `AccountId` order, matching PostgreSQL's native ordering of the
same identifiers, independent of which account is named source or destination.

#### Scenario: Lock order matches PostgreSQL's native ordering

- **WHEN** a transfer between two accounts is submitted, in either direction
- **THEN** the account with the smaller canonical identifier has its row locked first, regardless of
  which account is source and which is destination

#### Scenario: Many concurrent opposite-direction transfers never deadlock

- **WHEN** many concurrent transfers alternate direction between the same two accounts
- **THEN** every transfer completes with a success or a predicted failure, and none aborts due to a
  database deadlock

### Requirement: A race loser observes the up-to-date balance after its lock is granted

An operation that had to wait for an account row lock held by a concurrent operation SHALL evaluate
its business rules against the balance as committed by the operation that held the lock, not against
a balance read before the wait began.

#### Scenario: Two concurrent debits past half the balance

- **WHEN** an account with balance 1000 receives two concurrent debits of 700 each
- **THEN** exactly one debit succeeds, the other fails with insufficient funds, and the final balance
  is 300

### Requirement: Independent accounts are never blocked by an unrelated operation's lock

A row lock held for one account SHALL NOT delay an operation on a different, unrelated account.

#### Scenario: An unrelated account proceeds while another is locked

- **WHEN** account A's row lock is held by an in-progress operation and a concurrent operation targets
  only account B
- **THEN** the operation on B completes without waiting for A's lock to release

### Requirement: Locking a non-existent account fails without partial effect

Attempting to lock an account identifier with no corresponding row SHALL fail without acquiring any
further lock in the same operation and without producing any persisted state.

#### Scenario: Unknown account referenced by a financial operation

- **WHEN** a credit, debit, or transfer references an account identifier with no corresponding row
- **THEN** the operation fails, no balance changes anywhere, and no transaction or ledger row is
  persisted

### Requirement: A rolled-back operation leaves no persisted trace

When any step of an operation fails after an account has been locked but before the atomic unit
completes, no row it would have written SHALL be visible afterward, in any of the tables it touches.

#### Scenario: Insufficient funds leaves nothing behind

- **WHEN** a debit or transfer fails for insufficient funds
- **THEN** the account balance is unchanged and no transaction, ledger entry, or processed-transaction
  record exists for that attempt

### Requirement: A committed balance change survives a process restart

Once an operation's atomic unit has committed, its effect on the account balance SHALL be durable and
observable after the application process restarts.

#### Scenario: Balance persists across a restart

- **WHEN** a credit has committed and the application process is then restarted
- **THEN** reading the account's balance after restart returns the post-credit value

### Requirement: Balance reads never open a transaction or wait on a write lock

Reading an account's current balance SHALL complete without acquiring a row lock and without being
delayed by a concurrent write holding one.

#### Scenario: A balance read proceeds while the account is locked

- **WHEN** a write operation holds a row lock on an account
- **THEN** a concurrent read of that account's balance completes without waiting for the lock to
  release, returning the balance as most recently committed

### Requirement: The idempotency gate always fails open

When the advisory idempotency store is unreachable, times out, or returns an unparseable answer, the
gate SHALL report no prior knowledge of the transaction id rather than blocking the request or
answering incorrectly, and the exactly-once guarantee SHALL still hold via the authoritative store.

#### Scenario: Advisory store is down during a duplicate submission

- **WHEN** the same `transactionId` is submitted twice while the advisory idempotency store is
  unreachable for both submissions
- **THEN** each submission completes within a bounded time and at most one of them changes any balance

### Requirement: A transaction id reused with different parameters is rejected explicitly

A `transactionId` that was already applied SHALL, if resubmitted with different operation parameters
(different account, amount, or operation type), fail with a distinct, stable error code rather than
being silently accepted as a duplicate or surfacing as an unclassified failure.

#### Scenario: Same transaction id, different amount

- **WHEN** a `transactionId` already applied to a credit of 100 is resubmitted as a credit of 200 on
  the same account
- **THEN** the second request fails with a distinct error code identifying the conflict, and the
  account balance reflects only the original application
