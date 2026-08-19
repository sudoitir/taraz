## Context

See `proposal.md` for motivation. Current state: `core/domain` and `core/application` are complete,
unit-tested against hand-written in-memory fakes (`FakeAccountRepository` models row locking with a
per-account `ReentrantLock` acquired in canonical order), and merged. The REST driving adapter
(ADR-0043) is also merged: `CorrelationIdFilter` originates `X-Correlation-ID` correlation into MDC key `correlation_id`,
`Idempotency-Key` carries the domain `transactionId`, `ProblemFactory`/`ProblemAdvice` render RFC 7807
problems, and `CreateAccountUseCase`/`CreateAccountHandler` exist and call
`accounts.saveAll(List.of(account))` for a brand-new, never-locked account.

Seven outbound ports have no implementation: `AccountRepository`, `TransactionRepository`,
`ProcessedTransactionStore`, `UnitOfWork`, `AccountBalanceReadRepository`, `IdempotencyGate`,
`OutboxAppender`. `db.changelog-master.xml` is empty. `adapters/driven/persistence` and
`adapters/driven/messaging` contain only `package-info.java`.

Binding constraints from `docs/adr/`: ADR-0006/0033 (module dependency direction, enforced by ArchUnit
in `architecture-tests`), ADR-0015 (flat JPA entities, no relation graph), ADR-0018 (transaction opens
exactly at the atomic unit), ADR-0021/0041 (Valkey advisory + fail-open, Postgres authoritative),
ADR-0026/0042 (pessimistic ordered row locks, canonical `AccountId` ordering aligned with PostgreSQL's
native `uuid` comparison — this design's actual deadlock analysis, not the stale ADR-0006/ADR-0017
citation in `openspec/config.yaml`'s current text, which this change also corrects), ADR-0036 (`Money`
is unbounded `BigDecimal`).

## Goals / Non-Goals

**Goals:**
- Make every outbound port production-ready against real PostgreSQL, Valkey, and Kafka.
- Reproduce, under real infrastructure via Testcontainers, every concurrency/idempotency/atomicity
  guarantee currently proven only against in-memory fakes.
- Resolve every conflict between an accepted ADR and what a real adapter requires by recording a new,
  narrower ADR — never by silently deviating from the accepted decision.
- Close the concrete correctness gaps this design work surfaced (open-in-view, Valkey password
  mismatch, unmapped new error codes, missing bean wiring).

**Non-Goals:**
- REST contract changes beyond the two new problem-detail mappings.
- Compensate handlers/endpoints (ADR-0035).
- Multi-instance outbox poller ordering / horizontal scaling.
- A distributed tracing backend.

## Decisions

### D1 — Concurrency: sequential ordered `SELECT ... FOR UPDATE`, not one multi-row query

`AccountRepositoryJpaAdapter.lockAllInIdOrder` sorts input ids through a `TreeSet<AccountId>` (using
`AccountId`'s ADR-0042 canonical comparator — dedupes too, so a caller passing one id twice cannot
self-deadlock on a re-entrant lock), then issues **N sequential** `em.find(AccountEntity.class, id,
LockModeType.PESSIMISTIC_WRITE)` calls in that order.

Alternative considered: one `SELECT ... WHERE id IN (:ids) ORDER BY id FOR UPDATE`. Rejected — ADR-0026's
deadlock-freedom claim is "impossible by design," and PostgreSQL does not document multi-row `FOR
UPDATE` acquisition order as a stable contract; a future query-plan change could silently reorder it.
The sequential form makes acquisition order a property of our code, which is the only thing ADR-0026's
proof can rest on. Cost: one extra round trip on a transfer (N≤2), zero on credit/debit.

`em.find` returning `null` is how `ACCOUNT_NOT_FOUND` is detected — never `em.getReference`, which
returns a lazy proxy and defers the SELECT, so the lock would never fire and `null` would never
happen.

**Deadlock analysis (transfer, per challenge rules):** deadlock requires a cycle in the lock
wait-for graph. Every transaction that locks two account rows locks them in one total order (ADR-0042's
canonical `AccountId` comparator, which matches PostgreSQL's native unsigned `uuid` ordering — not
Java's default `UUID.compareTo`, which disagrees with it for some pairs). Two transactions requesting
the same pair of rows therefore always request them in the same relative order; a cycle would require
one of them to request in the opposite order, which cannot happen. This is proven directly, not by
timeout or retry, and is checked by a Testcontainers test that holds a side-connection lock on the
smaller-ordered account and asserts a reverse-direction transfer blocks on it.

### D2 — Isolation: READ COMMITTED

`TransactionTemplateUnitOfWork` sets `ISOLATION_READ_COMMITTED`. Correctness comes from the row locks
(D1), not snapshot isolation: under READ COMMITTED, when a blocked `SELECT ... FOR UPDATE` unblocks,
PostgreSQL's `EvalPlanQual` re-reads the newest **committed** row version — exactly ADR-0026's "the
race loser queues, then decides against the updated balance." REPEATABLE READ was considered and
rejected: the loser would instead receive serialization error `40001`, forcing an application retry
loop — reintroducing the retry-based concurrency model ADR-0026 replaced ADR-0017 specifically to
avoid. SERIALIZABLE adds the same `40001` risk plus predicate locks that would needlessly serialize the
outbox poller against the write path.

### D3 — `UnitOfWork.Failure` means rollback via `setRollbackOnly`, never a thrown exception

`Result.Failure` returned from the transactional work sets `TransactionStatus.setRollbackOnly()` and
returns the value; it is never thrown. Because `inTransaction` is always the outermost transaction
(one call per handler, `PROPAGATION_REQUIRED`, nothing wraps it), Spring's *local* rollback-only path
returns cleanly with no commit — `UnexpectedRollbackException` is only reachable from a *global*
rollback-only path (an inner participating transaction marking a shared one), which never occurs here.
This makes ADR-0040's "Failure ⇒ rollback" contract implementable exactly as written, with no
exception-based side channel.

### D4 — Idempotency gate is a pure fail-open read-through cache, not a reservation

`CreditHandler`/`DebitHandler`/`TransferHandler` already branch `Won` and `Unknown` to the identical
code path (`apply(intent)`), and ADR-0041 states there is no `IN_PROGRESS` state a reader must
interpret. So `ValkeyIdempotencyGate.tryBegin` performs one `GET`; a parseable, genuinely completed
outcome returns `AlreadyApplied`, and **every other case** — miss, parse failure, timeout, connection
error — returns `Unknown`. No `SETNX` reservation is written. Implementing ADR-0021's original
reservation protocol (`IN_PROGRESS`, TTL, `DEL`-on-failure) as the live mechanism would recreate
exactly the crash window ADR-0041 exists to eliminate. Every failure path degrading to `Unknown`
requires more than a `try/catch`: Lettuce's default `DisconnectedBehavior.DEFAULT` queues commands
during a reconnect, turning a dead Valkey into a per-request stall up to the command timeout, so
`ClientOptions.DisconnectedBehavior.REJECT_COMMANDS` plus a short command/connect timeout (~200ms) are
required configuration, not optional tuning.

### D5 — Messaging owns the outbox end-to-end; the appender is JDBC, not JPA, joining via the shared `DataSource`

`adapters/driven/messaging` gets `spring-boot-starter-jdbc` (via `JdbcClient`), never
`spring-boot-starter-data-jpa`. Boot's `JpaTransactionManager` binds the active JPA transaction's
`Connection` as a `ConnectionHolder` on the shared `DataSource`; `JdbcClient` on that same `DataSource`
resolves the same physical connection via `DataSourceUtils.getConnection`, so the outbox INSERT commits
or rolls back with the account/transaction writes in the same database transaction — without a second
persistence stack or an explicit cross-module dependency on `persistence`. The outbox row stores the
**final serialized wire bytes** of the `IntegrationEvent` at append time; the polling publisher copies
them to Kafka verbatim, making it a dumb pipe immune to mapper drift between append and publish time.

### D6 — Kafka topology: one topic per aggregate type, narrowing ADR-0027

ADR-0027's text says topics are defined per event type. This design uses **`taraz.account.v1`**
(opened/credited/debited, keyed by `account_id`) and **`taraz.transaction.v1`**
(posted/compensated, keyed by `transaction_id`) instead — confirmed with the user during design review.
Per-event-type topics would put `account.credited` and `account.debited` on separate topics, and Kafka
only orders within one topic's partition, so a consumer rebuilding a balance from the stream could not
know which of a credit and a debit on the same account happened first. One topic per aggregate type,
partitioned by the aggregate's own id, preserves that ordering. Recorded as new ADR-0051, explicitly
narrowing ADR-0027 rather than silently deviating from it.

### D7 — Persistence failure translation by constraint name, never message text

`PersistenceFailureTranslator` catches `DataIntegrityViolationException`/`CannotAcquireLockException`/
`QueryTimeoutException`/etc. thrown out of `TransactionTemplate.execute` (constraint violations surface
at flush/commit, so the catch wraps the whole `execute` call, not the inner supplier) and matches on
the PostgreSQL constraint **name** — `pk_processed_transaction` / `uq_ledger_transaction_external_id`
for `TRANSACTION_ID_CONFLICT`, lock/timeout exception types for `CONCURRENCY_CONFLICT` — never on
exception message text, which is not a stable contract across PostgreSQL versions. A schema test
asserts these constraint names exist, so a rename cannot silently downgrade a `409` into an opaque
`500`.

### D8 — Schema identity: surrogate PK for `ledger_transaction`, natural key PK for `processed_transaction`

`Transaction`'s domain identity is a client-supplied string (`TransactionId`), but ADR-0016 mandates
UUIDv7 PKs for B-tree insert locality. A natural-key PK on `ledger_transaction` would put a 64-byte
varchar in every child `ledger_entry` row's FK and every FK index, which is worse for locality than one
surrogate UUIDv7 PK plus a `UNIQUE` constraint on the natural key. `processed_transaction`, by
contrast, has exactly one access path (`WHERE transaction_id = ?`), no children, and no second
identity — a surrogate PK there would be an index nobody queries, which ADR-0019 forbids as decorative.
Recorded as new ADR-0047, narrowing ADR-0016 for tables with a synthetic identity or a child table.

### D9 — `numeric` balance columns, narrowing ADR-0019

ADR-0019 says no `numeric` for integral amounts; ADR-0036 made `Money` an unbounded `BigDecimal` and
deleted `BALANCE_OVERFLOW` from the error catalog specifically to remove an artificial ceiling. A
`bigint` column would silently reimpose `Long.MAX_VALUE` as that ceiling. `numeric` is the only
faithful column type; integrality is enforced with a `CHECK (value = trunc(value))` constraint instead
of relying on the column type. Recorded as new ADR-0044.

### D10 — Correlation propagation: duplicated MDC-key constant, cross-checked by an architecture test

`messaging` must not depend on `adapters.driving.rest` (ArchUnit), and the outbound-ports package may
hold only interfaces/records/enums, so neither a new outbound port nor a constant in `port` cleanly
carries the MDC key name `correlation_id` from `CorrelationIdFilter` into `messaging`. Considered and rejected: an
outbound `CorrelationContext` port (pushes a transport concern through the domain-event port ADR-0009
keeps clean, and nothing in `core` actually needs the correlation id); a constant class in `port` (fails the
`ports_contain_only_contracts_and_value_types` ArchUnit rule); a new shared module (disproportionate
for one string). Chosen: promote the key to a public constant on `RestHeaders`, declare an equal
constant in `messaging`, and add an architecture test asserting the two literals stay equal — the
duplication becomes a build-enforced invariant instead of an unguarded magic string. Recorded as new
ADR-0052.

### D11 — Testcontainers integration tests stay inside `./mvnw test`

`@Testcontainers(disabledWithoutDocker = true)` plus a custom JUnit `ExecutionCondition` that throws
(not merely reports disabled) when `-Dtaraz.require.docker=true` is set and Docker is unavailable — the
`ci` Maven profile sets that system property. This keeps `./mvnw test` as the single standard command
`.claude/rules/challenge-testing.md` requires, satisfied on a Docker-less reviewer machine by a clean
skip, while making a Docker-less CI run fail loudly rather than silently pass with the concurrency
proof skipped. Considered and rejected: a failsafe/`verify` split — it would move the graded
concurrency evidence outside the challenge's named standard command. Recorded as new ADR-0053.

### D12 — Connection-pool backpressure closes an invisible starvation risk

`spring.jpa.open-in-view` is currently unset (defaults `true`), which under virtual threads and a
bounded Hikari pool holds a JDBC connection for an entire request's lifetime including view rendering —
with 1000 concurrent virtual threads against a small fixed pool, this is an invisible starvation
deadlock, not a slow path. Set to `false`. Hikari pool is fixed (`maximum-pool-size ==
minimum-idle`, sized against PostgreSQL's `max_connections`, not request concurrency — real capacity
comes from short transactions per ADR-0018/0026, not a bigger pool) with a short `connection-timeout`
(~2s) so exhaustion surfaces as the new typed `CONCURRENCY_CONFLICT` → 503 instead of an unbounded
queue with no metric. Recorded as new ADR-0054.

## Risks / Trade-offs

- **[Risk] Single-instance outbox poller ordering.** With more than one application instance polling,
  `FOR UPDATE SKIP LOCKED` can let instance B publish a later row before instance A publishes an
  earlier one. → **Mitigation:** documented explicitly as a known limitation (non-goal); horizontal
  scaling would need a key-partitioned poller, out of scope for this change.
- **[Risk] A hot single account still serializes.** Row-level locks mean concurrent operations on the
  *same* account queue behind each other. → **Mitigation:** this is ADR-0026's deliberate, already-
  accepted trade-off for correctness; not reopened here.
- **[Risk] `numeric` costs more than `bigint` per row and per comparison.** → **Mitigation:** accepted
  per ADR-0036's own trade-off statement; negligible at this challenge's scale.
- **[Risk] Poison outbox rows could starve the queue.** → **Mitigation:** exponential backoff via
  `next_attempt_at`, `max-attempts` alerting without auto-deletion — a financial event is never
  silently dropped.
- **[Risk] Two literal MDC-key constants (D10) could drift.** → **Mitigation:** guarded by a dedicated
  architecture test, not by convention alone.
- **[Risk] Fifteen new ADRs is a lot of process overhead for one change.** → **Mitigation:** each
  records a genuinely separable decision the AI-use expectation in the challenge requires being able to
  defend individually (design choice, concurrency management, idempotency guarantee, failure behavior,
  trade-offs); several could be folded later if reviewers find the volume excessive, but splitting now
  keeps each decision's rationale traceable to exactly what it changed.

## Migration Plan

No production deployment exists yet (challenge submission, not a live service) — no data migration or
rollback strategy is needed. Liquibase changesets apply to an empty database on first run;
`SchemaIT` proves a second run applies zero changesets (idempotent migration). Task groups below apply
incrementally, each ending in its own passing tests, so `main` is never left in a broken state between
task groups.
