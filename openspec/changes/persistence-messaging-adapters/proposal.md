## Why

Every outbound port in `core/application/port/.../ports/outbound/` — `AccountRepository`,
`TransactionRepository`, `ProcessedTransactionStore`, `UnitOfWork`, `AccountBalanceReadRepository`,
`IdempotencyGate`, `OutboxAppender` — has zero production implementation. `adapters/driven/persistence`
and `adapters/driven/messaging` contain only `package-info.java`, and
`container/src/main/resources/db/changelog/db.changelog-master.xml` is an empty shell. The service is
fully designed and unit-tested against hand-written in-memory fakes, but it cannot boot, cannot persist
a balance, and cannot publish an event. The challenge's core invariants — no negative balance, no lost
update, exactly-once `transactionId` effect, atomic transfer — are today proven only against a
`ReentrantLock`-based fake, not against real PostgreSQL row locks, a real Valkey gate, or a real
transactional outbox. This change makes the service actually run and re-proves those invariants against
real infrastructure via Testcontainers.

## What Changes

- Liquibase schema (ADR-0014/0019): `account`, `ledger_transaction`, `ledger_entry`,
  `processed_transaction` (owned by `persistence`), `outbox` (owned by `messaging`), with column
  ordering, indexes, autovacuum tuning, and session lock/statement timeouts per ADR-0019's discipline.
- `adapters/driven/persistence`: JPA entities + MapStruct mappers (ADR-0015/0031) implementing
  `AccountRepository` (sequential ordered `SELECT ... FOR UPDATE`, ADR-0026/0042),
  `TransactionRepository`, `ProcessedTransactionStore`, `UnitOfWork` (`TransactionTemplate`,
  `READ_COMMITTED`, ADR-0018/0040), `AccountBalanceReadRepository` (plain `JdbcClient`, no
  transaction/lock — ADR-0007), and `IdempotencyGate` on Valkey as a pure fail-open read-through cache
  (ADR-0020/0021/0041, superseding ADR-0021's reservation protocol in behavior, as ADR-0041 already
  does on paper).
- `adapters/driven/messaging`: owns the outbox end-to-end — `OutboxAppender` (enlists in the caller's
  transaction via the shared `PlatformTransactionManager`, ADR-0010), the `IntegrationEvent` contract
  separate from `DomainEvent` (ADR-0009), and a `FOR UPDATE SKIP LOCKED` polling publisher to Kafka
  (ADR-0027) with exponential backoff, `max-attempts` alerting, and retention cleanup.
- Container wiring: missing `Clock`/`IdGenerator`/`PostingService` beans, `spring.jpa.open-in-view:
  false` (closes an invisible connection-pool starvation risk under virtual threads), fixed Hikari
  pool sized for backpressure not throughput, corrected `VALKEY_PASSWORD` default, Kafka producer
  config, and a corrected logging pattern that actually prints the `correlation_id` MDC key.
- Correlation propagation: the correlation id already originates at `CorrelationIdFilter` (ADR-0043); this change
  carries it through the three remaining hops — into the outbox row, onto a Kafka header, and restored
  into the publisher's MDC — with the shared constant duplicated and cross-checked by an architecture
  test rather than introducing a new coupling between `messaging` and `rest`.
- Two new stable error codes surfaced end to end: `TRANSACTION_ID_CONFLICT` (409 — ADR-0041's
  "different parameters, same id" last guard, detected by matching the `processed_transaction` /
  `ledger_transaction` constraint names, never message text) and `CONCURRENCY_CONFLICT` (503 — lock
  timeout / pool exhaustion), both mapped in `ProblemFactory`/`ProblemAdvice`.
- Two `LayerBoundariesTest` (ArchUnit) rules: driven adapters never depend on
  `core.application.service`; `persistence` and `messaging` never depend on each other.
- Fifteen new ADRs (0044–0058, see design.md, ADR-0056, ADR-0057, and ADR-0058) recording every decision
  that narrows an existing ADR or resolves a conflict the design work surfaced, created with this change
  per `.claude/rules/adr.md` ("before or with the change, not after").
- Testcontainers integration test suite proving the challenge's concurrency, idempotency, and transfer
  scenarios against real PostgreSQL/Valkey/Kafka, staying inside `./mvnw test` (auto-skip without
  Docker, CI turns skip into failure) per `.claude/rules/challenge-testing.md`.
- Corrects staleness in already-shipped artifacts this work exposed: `openspec/config.yaml`'s
  superseded ADR-0017/`@Version` references, the `project-scaffolding` spec's stale four-flat-module
  requirement (ADR-0033 deepened it), `AccountRepository`/`IdempotencyGate` javadoc that no longer
  matches the implemented contract, missing `messaging` pom dependencies, and missing JaCoCo wiring.

## Capabilities

### New Capabilities

- `persistence-adapter`: PostgreSQL/JPA implementation of the write-side repositories, the
  transaction-boundary `UnitOfWork`, the read-side balance repository, and the Valkey idempotency gate.
- `messaging-adapter`: the transactional outbox (append + schema), the `IntegrationEvent` contract, and
  the Kafka polling publisher.

### Modified Capabilities

- `balance-application-layer`: adds the ADR-0041 last-guard requirement — a `transactionId` reused with
  different operation parameters SHALL fail with a distinct, stable error code
  (`TRANSACTION_ID_CONFLICT`) detected at the persistence boundary, never silently accepted and never
  an unclassified failure; clarifies `AccountRepository.saveAll`'s contract to explicitly cover both
  the insert case (an account never locked, e.g. account creation) and the update case (an
  already-locked account).
- `rest-api`: the error-code-to-status mapping requirement gains two entries — `TRANSACTION_ID_CONFLICT`
  → 409, `CONCURRENCY_CONFLICT` → 503 (with `Retry-After`).
- `project-scaffolding`: corrects the "four Maven modules" requirement to the nested hierarchy ADR-0033
  actually established (`core/domain`, `core/application/{port,service,query}`,
  `adapters/driving/rest`, `adapters/driven/{persistence,messaging}`, `container`,
  `architecture-tests`); the one-command-build-and-test requirement is reaffirmed as still holding
  without Docker — this change's integration tests auto-skip rather than fail — and gains a scenario
  covering that behavior explicitly.

## Impact

- **New code**: `adapters/driven/persistence/**`, `adapters/driven/messaging/**`, Liquibase changesets
  under both adapters, `container` configuration classes and test resources, new ArchUnit rules, new
  Testcontainers test classes in `container/src/test`.
- **Modified code**: `core/domain/.../ErrorCode.java` (two new codes), `adapters/driving/rest/.../
  ProblemFactory.java` and `ProblemAdvice.java` (new mappings), `core/application/port/.../
  AccountRepository.java` and `IdempotencyGate.java` (javadoc corrections to match the implemented
  contract), `adapters/driven/messaging/pom.xml`, `container/pom.xml`,
  `architecture-tests/.../LayerBoundariesTest.java`, `container/src/main/resources/application.yaml`,
  `openspec/config.yaml`, `openspec/specs/project-scaffolding/spec.md`, `pom.xml` (JaCoCo), `README.md`
  (status section), `justfile` if needed for a Docker-required IT profile.
- **New infra dependencies**: none beyond what `compose.yaml` already provisions (PostgreSQL 18,
  Valkey 9, Kafka 4.3.1) — this change only makes the application actually use them. ShedLock
  (ADR-0057, scope amendment) adds one library dependency and one small coordination table on the
  same PostgreSQL instance — no third infrastructure service.
- **REST contract changes**: the two new error-code mappings, the correlation header rename
  (`X-Flow-ID` → `X-Correlation-ID`, ADR-0056, scope amendment), and JSON field naming switching from
  snake_case to camelCase (ADR-0058, scope amendment — requested by the developer mid-apply); no change
  to `core/domain` business logic; compensate handlers remain out of scope (ADR-0035).
- **Two pre-existing correctness bugs found and fixed while proving the app actually boots** (see
  tasks.md 9a.2): `container/pom.xml` was missing `spring-boot-liquibase` (Boot 4's Liquibase
  *autoconfiguration* artifact — migrations never ran through the Spring-managed app before this
  change, only through Testcontainers-direct test paths); and `container/src/test/resources/
  application.yaml` shadowed rather than merged with the main `application.yaml` on the test
  classpath, so every integration test ran with zero datasource/liquibase/redis/kafka configuration
  from the main file — fixed by renaming to a profile-specific `application-test.yaml`.

## Non-goals

- REST driving adapter behavior beyond the two new problem-detail mappings (already shipped, ADR-0043).
- Compensate command handlers/endpoints (ADR-0035) — still future work.
- Horizontal scaling of the outbox poller / multi-instance publish ordering — documented as a known
  limitation of the single-instance `FOR UPDATE SKIP LOCKED` design, not solved here.
- Distributed tracing backend (Micrometer Tracing, W3C `traceparent`) — correlation stays a plain
  `correlation_id` string per ADR-0043; adopting a tracing backend is a future, separate decision.
- k6 load testing (ADR-0022 mentions it; no harness exists yet and none is added here).
