## 1. New ADRs and existing-ADR staleness fixes

- [ ] 1.1 Write ADR-0044 (numeric balance columns, narrows ADR-0019), ADR-0045 (sequential ordered
      `FOR UPDATE`, not one multi-row query), ADR-0046 (READ COMMITTED isolation + timeout ordering),
      ADR-0047 (surrogate PK for `ledger_transaction`, natural key for `processed_transaction`, narrows
      ADR-0016) per design.md D1/D2/D8/D9, following `docs/adr/000-template.md`, Persian,
      `<div dir="rtl">`, status پذیرفته‌شده.
- [ ] 1.2 Write ADR-0048 (constraint-name failure translation, two new error codes), ADR-0049
      (messaging owns the outbox end-to-end), ADR-0050 (integration-event contract shape, outbox
      stores final wire bytes), ADR-0051 (Kafka topic-per-aggregate-type, narrows ADR-0027) per
      design.md D5/D6/D7.
- [ ] 1.3 Write ADR-0052 (correlation propagation via duplicated MDC constant + architecture test),
      ADR-0053 (Testcontainers stay in surefire, Docker-absent auto-skip + CI enforcement flag),
      ADR-0054 (Hikari sizing + `open-in-view: false`), ADR-0055 (outbox delivery policy: backoff,
      max-attempts, retention, single-poller ordering caveat) per design.md D10/D11/D12.
- [ ] 1.4 Fix `openspec/config.yaml`: replace the `@Version` mention in the tech-stack context block
      and the `ADR-0006, ADR-0017` deadlock-analysis citation in `rules.design` with `ADR-0026,
      ADR-0042`.
- [ ] 1.5 `./mvnw test` (no code changed yet — confirms the baseline is still green before touching
      anything).

## 2. Error catalog and REST error mapping

- [ ] 2.1 Add `TRANSACTION_ID_CONFLICT` and `CONCURRENCY_CONFLICT` to
      `core/domain/.../common/ErrorCode.java` with javadoc referencing ADR-0041 and ADR-0048.
- [ ] 2.2 Add both to `ProblemFactory.STATUS_BY_CODE` (409, 503) in `adapters/driving/rest`; add a
      `Retry-After` header on the 503 response path.
- [ ] 2.3 Correct `AccountRepository`'s javadoc (`core/application/port/.../outbound/
      AccountRepository.java`) to document that `saveAll` handles both the insert case (an account
      never locked, e.g. account creation) and the update case (an already-locked account) — per
      design.md and the `balance-application-layer` spec delta.
- [ ] 2.4 Correct `IdempotencyGate.release`'s javadoc to remove the "stale reservation" language (the
      reservation protocol is not implemented — design.md D4) and document `GateDecision.Won` as
      unreachable for this adapter.
- [ ] 2.5 Unit tests: `ErrorCode` catalog test, `ProblemFactory` mapping test for both new codes.
      `./mvnw test`.

## 3. Liquibase schema

- [ ] 3.1 Create `adapters/driven/persistence/src/main/resources/db/changelog/persistence/` with
      `0001-account.sql`, `0002-ledger-transaction.sql`, `0003-ledger-entry.sql`,
      `0004-processed-transaction.sql`, `0006-session-timeouts.sql` per design.md D1/D2/D8/D9 —
      exact column ordering (fixed-width first), `numeric` balance columns with `CHECK` integrality,
      no `@Version` column, named constraints `pk_processed_transaction` and
      `uq_ledger_transaction_external_id`, indexes each serving one named query, autovacuum tuning on
      `account`.
- [ ] 3.2 Create `adapters/driven/messaging/src/main/resources/db/changelog/messaging/0005-outbox.sql`
      per design.md D5 — no FK to ledger tables, partial indexes for poller and cleanup, aggressive
      autovacuum.
- [ ] 3.3 Wire `container/src/main/resources/db/changelog/db.changelog-master.xml` with explicit
      `<include>` (never `<includeAll>`) in the order above.
- [ ] 3.4 `SchemaIT` (Testcontainers, `container/src/test`): Liquibase applies cleanly on an empty DB;
      a second run applies zero changesets; assert via `information_schema` that
      `account.balance_minor_units` is `numeric` and that `pk_processed_transaction` /
      `uq_ledger_transaction_external_id` exist by name.
- [ ] 3.5 `./mvnw test` (Docker required for this group's test to actually run; confirm it skips
      cleanly without Docker and passes with it).

## 4. Persistence entities and mappers

- [ ] 4.1 Add `spring-boot-starter-data-jpa` usage: `MoneyEmbeddable`, `AccountEntity`,
      `LedgerTransactionEntity`, `LedgerEntryEntity`, `ProcessedTransactionEntity` (flat, Lombok,
      ADR-0015/0031) under `adapters/driven/persistence/.../{common,account,transaction,idempotency}`.
- [ ] 4.2 `AccountPersistenceMapper`, `TransactionPersistenceMapper` (MapStruct) with the hand-written
      `Account.reconstitute(...).orElseThrow()` seam — no events on load.
- [ ] 4.3 Unit tests for both mappers (plain JUnit, no Spring context, no Docker) — round-trip
      entity↔domain for every field including `Money` normalization edge cases.
- [ ] 4.4 `./mvnw test`.

## 5. `AccountRepository`, `TransactionRepository`, `ProcessedTransactionStore`

- [ ] 5.1 Implement `AccountRepositoryJpaAdapter.lockAllInIdOrder` — sequential ordered
      `em.find(..., PESSIMISTIC_WRITE)` per design.md D1, `TreeSet<AccountId>` dedup+sort,
      `ACCOUNT_NOT_FOUND` on any miss.
- [ ] 5.2 Implement `AccountRepositoryJpaAdapter.saveAll` — `em.find` (NONE) then insert-if-null /
      dirty-check-update-if-present, never `em.merge`, per design.md and the corrected port javadoc.
- [ ] 5.3 Implement `TransactionRepositoryJpaAdapter.save` — persist the parent `ledger_transaction`
      row (surrogate id from the domain's `IdGenerator`) then each `ledger_entry` leg.
- [ ] 5.4 Implement `ProcessedTransactionStoreJpaAdapter` — `find`/`record` against the natural-key
      table, `CommandOutcome` round-tripped through `jsonb` with amounts as decimal strings.
- [ ] 5.5 `./mvnw test` (no Docker-backed tests wired yet — these adapters aren't exercised until
      task group 7).

## 6. `UnitOfWork`, failure translation, and container wiring

- [ ] 6.1 Implement `TransactionTemplateUnitOfWork` — `READ_COMMITTED` (design.md D2),
      `setRollbackOnly()` on `Failure` (design.md D3).
- [ ] 6.2 Implement `PersistenceFailureTranslator` — constraint-name matching for
      `TRANSACTION_ID_CONFLICT`, lock/timeout exception matching for `CONCURRENCY_CONFLICT` (design.md
      D7); unit test with hand-built `DataAccessException` fixtures asserting the match is by
      constraint name, not message text.
- [ ] 6.3 Add `DomainConfiguration` in `container` — `Clock.systemUTC()`, `IdGenerator`
      (`UuidV7IdGenerator`), `PostingService(ids)` beans.
- [ ] 6.4 `application.yaml`: `spring.jpa.open-in-view: false`, Hikari fixed-pool config (design.md
      D12), session-timeout GUC alignment, corrected `VALKEY_PASSWORD` default (`taraz`, matching
      `compose.yaml`), Liquibase enabled. `container/src/test/resources/application.yaml` disabling
      `spring.docker.compose` for tests.
- [ ] 6.5 Implement `AccountBalanceJdbcReadRepository` — plain `JdbcClient`, no transaction, no lock.
- [ ] 6.6 `./mvnw test`.

## 7. Postgres-backed concurrency and atomicity integration tests

- [ ] 7.1 Add Testcontainers dependencies to `container/pom.xml` (`spring-boot-testcontainers`,
      `testcontainers-postgresql`, already-present ones checked); shared static-container base class
      `AbstractTarazIT`; `@Testcontainers(disabledWithoutDocker = true)` +
      `RequireDockerWhenEnforced` `ExecutionCondition` reading `-Dtaraz.require.docker=true`
      (design.md D11); wire that system property into the `ci` Maven profile.
- [ ] 7.2 `ConcurrentSingleAccountIT` — 1000 barrier-synchronized ops on one account, exact final
      balance (`.claude/rules/challenge-concurrency.md` reference scenario).
- [ ] 7.3 `ConcurrentDebitExactlyOnceIT` — balance 1000, two concurrent debits of 700, exactly one
      succeeds, final balance 300.
- [ ] 7.4 `IndependentAccountsDoNotBlockIT` — deterministic proof via a held side-connection lock, not
      timing.
- [ ] 7.5 `ConcurrentOppositeTransfersNoDeadlockIT` — 200 alternating-direction transfers, assert zero
      `40P01` deadlocks.
- [ ] 7.6 `CanonicalLockOrderIT` — direct proof that the smaller-ordered account is locked first
      regardless of transfer direction.
- [ ] 7.7 `RollbackLeavesNoTraceIT` — insufficient-funds failure leaves zero rows in every touched
      table.
- [ ] 7.8 `./mvnw test` with Docker running — this group is the real proof the challenge grades.

## 8. Idempotency: Valkey gate and Postgres integration tests

- [ ] 8.1 Implement `ValkeyIdempotencyGate` — read-through cache only (design.md D4), adapter-owned
      JSON serialization (not the web `ObjectMapper`), `taraz:idem:v1:` key prefix, configurable TTL.
- [ ] 8.2 Configure Lettuce `ClientOptions.DisconnectedBehavior.REJECT_COMMANDS` + short
      command/connect timeouts (design.md D4) via a `LettuceClientConfigurationBuilderCustomizer`.
- [ ] 8.3 `IdempotencySequentialIT` — ×3 credit/debit/transfer with the same transaction id, exactly
      one effect each (`.claude/rules/challenge-idempotency.md` reference scenario).
- [ ] 8.4 `IdempotencyConcurrentIT` — N concurrent duplicates of the same transaction id, exactly one
      `APPLIED`, the rest `REPLAYED`.
- [ ] 8.5 `IdempotencyGateDownFailOpenIT` — stop/pause the Valkey container; exactly-once still holds
      via `processed_transaction`, and calls return within a bounded time (proves the fail-fast wiring,
      not just eventual correctness).
- [ ] 8.6 `TransactionIdConflictIT` — same transaction id, different account/amount → `409` with code
      `TRANSACTION_ID_CONFLICT`, not a leaked exception.
- [ ] 8.7 `./mvnw test` with Docker running.

## 9. Messaging: outbox appender, integration-event contract, Kafka publisher

- [ ] 9.1 Add `spring-boot-starter-jdbc`, `spring-boot-starter-kafka`, `spring-boot-starter-json`,
      `mapstruct`, `lombok` to `adapters/driven/messaging/pom.xml`, plus its own
      `maven-compiler-plugin` `annotationProcessorPaths` override (the root POM does not merge this
      list — must be copied, not inherited); update the module's stale "structural placeholder"
      description.
- [ ] 9.2 Define `IntegrationEventEnvelope` + versioned `...V1` payload records
      (`AccountOpenedV1`/`AccountCreditedV1`/`AccountDebitedV1`/`TransactionPostedV1`/
      `TransactionCompensatedV1`) per design.md D5 — amounts as decimal strings, snake_case on the
      wire via a messaging-owned `ObjectMapper`.
- [ ] 9.3 Implement `IntegrationEventFactory` — exhaustive `DomainEvent → IntegrationEventEnvelope`
      dispatch, throws on an unmapped event type (never silently drops a financial event); a
      completeness test enumerating every domain event class and asserting the factory maps it.
- [ ] 9.4 Implement `JdbcOutboxAppender` — one INSERT per event, `CAST(? AS jsonb)`, reads the current
      flow id from MDC (falls back to `NULL`, never synthesizes — design.md D10), computes topic +
      partition key at append time per design.md D6.
- [ ] 9.5 Unit tests: `IntegrationEventFactory` completeness test; `JdbcOutboxAppender` against an
      in-memory/H2-free fixture is not meaningful here — defer its correctness proof to the
      Testcontainers test in 9.8; keep this task to what's unit-testable (factory dispatch, payload
      serialization shape).
- [ ] 9.6 Implement `OutboxPollingPublisher` — `FOR UPDATE SKIP LOCKED` claim, send-then-mark,
      exponential backoff via `next_attempt_at`, `max-attempts` alerting without deletion, retention
      cleanup; `@Scheduled` with `spring.task.scheduling.simple.concurrency-limit: 1`.
- [ ] 9.7 Kafka producer config in `application.yaml` (`acks=all`, `enable.idempotence=true`,
      `max.in.flight=5`, `ByteArraySerializer`) per design.md D5/D6; topic names as configuration
      properties.
- [ ] 9.8 `RollbackLeavesNoOutboxRowIT`, `OutboxPublishedExactlyOncePerOccurrenceIT` (real
      `KafkaConsumer`, dedup by event id, assert partition key), `CorrelationPropagationIT` (flow id
      reaches the outbox row and the Kafka header; absent flow id leaves both null/omitted).
- [ ] 9.9 `./mvnw test` with Docker running.

## 10. Correlation constant promotion and architecture enforcement

- [ ] 10.1 Promote `FlowIdFilter.MDC_KEY` to a public constant on `RestHeaders`
      (`FLOW_ID_MDC_KEY`); update `FlowIdFilter` to reference it. Declare the matching constant in
      `messaging` (`MessagingCorrelation.FLOW_ID_MDC_KEY`).
- [ ] 10.2 Add `logging.pattern.level` to `application.yaml` so `%X{flow_id}` actually appears in log
      output (currently doesn't — the `rest-api` spec's "same value appears in logs" scenario cannot
      pass today).
- [ ] 10.3 Add two new rules to `architecture-tests/.../LayerBoundariesTest.java`: `..adapters.driven..`
      never depends on `..core.application.service..`; `persistence` and `messaging` never depend on
      each other. Add the flow-id-constant equality test (`RestHeaders.FLOW_ID_MDC_KEY.equals(
      MessagingCorrelation.FLOW_ID_MDC_KEY)`).
- [ ] 10.4 `./mvnw test`.

## 11. Project-scaffolding and build/CI corrections

- [ ] 11.1 Add JaCoCo to root `pom.xml` (the README already carries SonarCloud coverage badges with
      no coverage ever produced today).
- [ ] 11.2 Confirm `.github/workflows/ci.yml`'s `-Pci verify` run passes
      `-Dtaraz.require.docker=true` (design.md D11/ADR-0053) so a Docker-less CI run fails loudly.
- [ ] 11.3 Add `.env.example` entry for `KAFKA_BOOTSTRAP_SERVERS`.
- [ ] 11.4 Update `README.md`'s status section: remove the "real adapters have not yet replaced the
      fakes" language; document the Valkey advisory / Postgres authoritative idempotency design, the
      real `SELECT ... FOR UPDATE` deadlock-free proof (now backed by Testcontainers, not just the
      fakes), and the outbox/Kafka event flow, per the challenge's required README sections
      (`.claude/rules/docs-fa.md`).
- [ ] 11.5 `./mvnw test` and `./mvnw -B -Pci verify` both green, with and without Docker as documented
      in the `project-scaffolding` spec delta.

## 12. Final verification

- [ ] 12.1 `just up && just run`: application boots against real compose infra.
- [ ] 12.2 Manual smoke test: `POST /accounts` → `POST .../credits` (with `Idempotency-Key`) →
      `GET .../balance` round-trips; duplicate `Idempotency-Key` with a different amount returns `409`
      with `code: TRANSACTION_ID_CONFLICT`.
- [ ] 12.3 Confirm published events land on `taraz.account.v1` / `taraz.transaction.v1` with the
      `X-Flow-ID` Kafka header present when the originating request carried one.
- [ ] 12.4 Full `./mvnw test` run (Docker present) green end to end; re-run once more with Docker
      stopped to confirm the clean-skip path still holds.
