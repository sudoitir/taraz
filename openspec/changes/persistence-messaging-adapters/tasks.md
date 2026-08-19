## 1. New ADRs and existing-ADR staleness fixes

- [x] 1.1 Write ADR-0044 (numeric balance columns, narrows ADR-0019), ADR-0045 (sequential ordered
      `FOR UPDATE`, not one multi-row query), ADR-0046 (READ COMMITTED isolation + timeout ordering),
      ADR-0047 (surrogate PK for `ledger_transaction`, natural key for `processed_transaction`, narrows
      ADR-0016) per design.md D1/D2/D8/D9, following `docs/adr/000-template.md`, Persian,
      `<div dir="rtl">`, status پذیرفته‌شده.
- [x] 1.2 Write ADR-0048 (constraint-name failure translation, two new error codes), ADR-0049
      (messaging owns the outbox end-to-end), ADR-0050 (integration-event contract shape, outbox
      stores final wire bytes), ADR-0051 (Kafka topic-per-aggregate-type, narrows ADR-0027) per
      design.md D5/D6/D7.
- [x] 1.3 Write ADR-0052 (correlation propagation via duplicated MDC constant + architecture test),
      ADR-0053 (Testcontainers stay in surefire, Docker-absent auto-skip + CI enforcement flag),
      ADR-0054 (Hikari sizing + `open-in-view: false`), ADR-0055 (outbox delivery policy: backoff,
      max-attempts, retention, single-poller ordering caveat) per design.md D10/D11/D12.
- [x] 1.4 Fix `openspec/config.yaml`: replace the `@Version` mention in the tech-stack context block
      and the `ADR-0006, ADR-0017` deadlock-analysis citation in `rules.design` with `ADR-0026,
      ADR-0042`.
- [x] 1.5 `./mvnw test` (no code changed yet — confirms the baseline is still green before touching
      anything). 139 tests, 0 failures.

## 2. Error catalog and REST error mapping

- [x] 2.1 Add `TRANSACTION_ID_CONFLICT` and `CONCURRENCY_CONFLICT` to
      `core/domain/.../common/ErrorCode.java` with javadoc referencing ADR-0041 and ADR-0048.
- [x] 2.2 Add both to `ProblemFactory.STATUS_BY_CODE` (409, 503) in `adapters/driving/rest`; add a
      `Retry-After` header on the 503 response path.
- [x] 2.3 Correct `AccountRepository`'s javadoc (`core/application/port/.../outbound/
      AccountRepository.java`) to document that `saveAll` handles both the insert case (an account
      never locked, e.g. account creation) and the update case (an already-locked account) — per
      design.md and the `balance-application-layer` spec delta.
- [x] 2.4 Correct `IdempotencyGate.release`'s javadoc to remove the "stale reservation" language (the
      reservation protocol is not implemented — design.md D4) and document `GateDecision.Won` as
      unreachable for this adapter.
- [x] 2.5 Unit tests: `ErrorCode` catalog test, `ProblemFactory` mapping test for both new codes.
      `./mvnw test`. 147 tests, 0 failures.

## 3. Liquibase schema

- [x] 3.1 Create `adapters/driven/persistence/src/main/resources/db/changelog/persistence/` with
      `0001-account.sql`, `0002-ledger-transaction.sql`, `0003-ledger-entry.sql`,
      `0004-processed-transaction.sql`, `0006-session-timeouts.sql` per design.md D1/D2/D8/D9 —
      exact column ordering (fixed-width first), `numeric` balance columns with `CHECK` integrality,
      no `@Version` column, named constraints `pk_processed_transaction` and
      `uq_ledger_transaction_external_id`, indexes each serving one named query, autovacuum tuning on
      `account`.
- [x] 3.2 Create `adapters/driven/messaging/src/main/resources/db/changelog/messaging/0005-outbox.sql`
      per design.md D5 — no FK to ledger tables, partial indexes for poller and cleanup, aggressive
      autovacuum.
- [x] 3.3 Wire `container/src/main/resources/db/changelog/db.changelog-master.xml` with explicit
      `<include>` (never `<includeAll>`) in the order above.
- [x] 3.4 `SchemaIT` (Testcontainers, `container/src/test`): Liquibase applies cleanly on an empty DB;
      a second run applies zero changesets; assert via `information_schema` that
      `account.balance_minor_units` is `numeric` and that `pk_processed_transaction` /
      `uq_ledger_transaction_external_id` exist by name. Built context-free (no Spring context — the
      full app can't boot until every outbound port has a bean), via `TarazDockerTest` +
      `AbstractTarazIT`'s shared static `PostgreSQLContainer`.
- [x] 3.5 `./mvnw test` (Docker required for this group's test to actually run; confirm it skips
      cleanly without Docker and passes with it). Found and fixed a real gap: surefire's default
      include patterns don't match `*IT.java` (that's failsafe's convention) — added explicit
      `<includes>` to the root pom's surefire config, without which ADR-0053 would be silently false.
      150 tests, 0 failures with Docker running.

## 4. Persistence entities and mappers

- [x] 4.1 Add `spring-boot-starter-data-jpa` usage: `MoneyEmbeddable`, `AccountEntity`,
      `LedgerTransactionEntity`, `LedgerEntryEntity` (flat, Lombok, ADR-0015/0031) under
      `adapters/driven/persistence/.../{common,account,transaction}`. `ProcessedTransactionEntity`
      moved to task 5.4 alongside its store adapter (natural cohesion — same file, same task).
- [x] 4.2 `AccountPersistenceMapper`, `TransactionPersistenceMapper` (MapStruct) with the hand-written
      `Account.reconstitute(...).orElseThrow()` seam — no events on load.
- [x] 4.3 Unit tests for both mappers (plain JUnit, no Spring context, no Docker) — round-trip
      entity↔domain for every field including `Money` normalization edge cases (scale-blind
      `100.00` == `100`). Added missing `junit-jupiter`/`assertj-core` test deps to
      `adapters/driven/persistence/pom.xml` (none existed — this is the module's first test).
- [x] 4.4 `./mvnw test`. 156 tests, 0 failures.

## 5. `AccountRepository`, `TransactionRepository`, `ProcessedTransactionStore`

- [x] 5.1 Implement `AccountRepositoryJpaAdapter.lockAllInIdOrder` — sequential ordered
      `em.find(..., PESSIMISTIC_WRITE)` per design.md D1, `TreeSet<AccountId>` dedup+sort,
      `ACCOUNT_NOT_FOUND` on any miss.
- [x] 5.2 Implement `AccountRepositoryJpaAdapter.saveAll` — `em.find` (NONE) then insert-if-null /
      dirty-check-update-if-present, never `em.merge`, per design.md and the corrected port javadoc.
- [x] 5.3 Implement `TransactionRepositoryJpaAdapter.save` — persist the parent `ledger_transaction`
      row (surrogate id from the domain's `IdGenerator`) then each `ledger_entry` leg.
- [x] 5.4 Implement `ProcessedTransactionEntity` (natural-key PK, jsonb `outcome`) and
      `ProcessedTransactionStoreJpaAdapter` — `find`/`record` against the natural-key table via
      `ProcessedOutcomeCodec` (adapter-owned Jackson mapper, amounts as decimal strings, shared
      between this store and the future Valkey gate so both agree on one snapshot shape). `record`
      resolves the sibling `ledger_transaction`'s surrogate FK by querying on `external_id` — AUTO
      flush mode makes the not-yet-committed row from the same transaction visible.
      Found and fixed: `@PersistenceContext` cannot target a constructor parameter (JPA restricts it
      to fields/setters) — removed it from all three adapters; the shared `EntityManager` bean these
      constructors need is deferred to task 6.3's container wiring
      (`SharedEntityManagerCreator.createSharedEntityManager`).
- [x] 5.5 `./mvnw test` (no Docker-backed tests wired yet — these adapters aren't exercised until
      task group 7). Added a unit test for `ProcessedOutcomeCodec` (pure POJO, no DB needed) covering
      round-trip, multi-balance transfers, schema-version rejection, and BigDecimal precision.
      160 tests, 0 failures.

## 6. `UnitOfWork`, failure translation, and container wiring

- [x] 6.1 Implement `TransactionTemplateUnitOfWork` — `READ_COMMITTED` (design.md D2),
      `setRollbackOnly()` on `Failure` (design.md D3).
- [x] 6.2 Implement `PersistenceFailureTranslator` — constraint-name matching for
      `TRANSACTION_ID_CONFLICT`, lock/timeout exception matching for `CONCURRENCY_CONFLICT` (design.md
      D7); unit test with hand-built `DataAccessException`/`PSQLException` fixtures (raw Postgres
      wire-format `ServerErrorMessage`) asserting the match is by constraint name — each fixture
      deliberately carries an unrelated message string, so a text-based match would fail where the
      name-based one passes. Added `org.postgresql:postgresql` as a compile dependency of
      `persistence` (previously only a `container` runtime dep) since this adapter is PostgreSQL-
      specific by design (ADR-0013) and needs `PSQLException`/`ServerErrorMessage` at compile time.
- [x] 6.3 Add `DomainConfiguration` in `container` — `Clock.systemUTC()`, `IdGenerator`
      (`UuidV7IdGenerator`), `PostingService(ids)` beans. Also added `PersistenceConfiguration`
      exposing a shared `EntityManager` bean via `SharedEntityManagerCreator` — found that
      `@PersistenceContext` cannot target a constructor parameter (JPA restricts it to fields/
      setters), so this bean is what lets the three JPA repository adapters (task 5) stay
      constructor-injected rather than falling back to field injection.
- [x] 6.4 `application.yaml`: `spring.jpa.open-in-view: false`, Hikari fixed-pool config (design.md
      D12), corrected `VALKEY_PASSWORD` default (`taraz`, matching `compose.yaml` — was empty,
      silently masked by the gate's fail-open behavior), Valkey command/connect timeouts, Liquibase
      already enabled. `container/src/test/resources/application.yaml` disabling
      `spring.docker.compose` for tests (added in task 3). Also declared
      `spring-boot-starter-validation` directly on `container` rather than relying on the transitive
      from `rest` (`CommandValidator` needs it; a future decoupling of REST would otherwise silently
      break every write handler at startup).
- [x] 6.5 Implement `AccountBalanceJdbcReadRepository` — plain `JdbcClient`, no transaction, no lock.
- [x] 6.6 `./mvnw test`. 166 tests, 0 failures.

## 7. Postgres-backed concurrency and atomicity integration tests

- [x] 7.1 Add Testcontainers dependencies to `container/pom.xml` (`spring-boot-testcontainers`,
      `testcontainers-postgresql`, already-present ones checked); shared static-container base class
      `AbstractTarazIT`; `@Testcontainers(disabledWithoutDocker = true)` +
      `RequireDockerWhenEnforced` `ExecutionCondition` reading `-Dtaraz.require.docker=true`
      (design.md D11); wire that system property into the `ci` Maven profile.
- [x] 7.2 `ConcurrentSingleAccountIT` — 1000 barrier-synchronized ops on one account, exact final
      balance (`.claude/rules/challenge-concurrency.md` reference scenario). A barrier-synchronized
      burst of 1000 threads on one account legitimately exceeds Hikari's ADR-0054 2s connection-timeout
      for some callers (only ~32 can be in flight, and all 1000 serialize behind one row lock) — real
      overload backpressure, not a bug. Found while running this group: `CannotCreateTransactionException`
      (Hikari pool exhaustion) was escaping `PersistenceFailureTranslator` untranslated because it is a
      `TransactionException`, not a `DataAccessException` — fixed by adding it to
      `isConcurrencyConflict` (translator + new unit test). The test retries `CONCURRENCY_CONFLICT` with
      the same idempotency key via a new `TestConcurrency.runConcurrentlyRetryingOnBackpressure` helper,
      matching real client retry behavior against a 503.
- [x] 7.3 `ConcurrentDebitExactlyOnceIT` — balance 1000, two concurrent debits of 700, exactly one
      succeeds, final balance 300.
- [x] 7.4 `IndependentAccountsDoNotBlockIT` — deterministic proof via a held side-connection lock, not
      timing.
- [x] 7.5 `ConcurrentOppositeTransfersNoDeadlockIT` — 200 alternating-direction transfers, assert zero
      `40P01` deadlocks. Same backpressure-retry treatment as 7.2.
- [x] 7.6 `CanonicalLockOrderIT` — direct proof that the smaller-ordered account is locked first
      regardless of transfer direction.
- [x] 7.7 `RollbackLeavesNoTraceIT` — insufficient-funds failure leaves zero rows in every touched
      table.
- [x] 7.8 `./mvnw test` with Docker running — this group is the real proof the challenge grades. All 6
      ITs green after the fixes above.

## 8. Idempotency: Valkey gate and Postgres integration tests

- [x] 8.1 Implement `ValkeyIdempotencyGate` — read-through cache only (design.md D4), adapter-owned
      JSON serialization (not the web `ObjectMapper`), `taraz:idem:v1:` key prefix, configurable TTL.
- [x] 8.2 Configure Lettuce `ClientOptions.DisconnectedBehavior.REJECT_COMMANDS` + short
      command/connect timeouts (design.md D4) via a `LettuceClientConfigurationBuilderCustomizer`
      (`ValkeyResilienceConfiguration`, 200ms timeout/connect-timeout).
- [x] 8.3 `IdempotencySequentialIT` — ×3 credit/debit/transfer with the same transaction id, exactly
      one effect each (`.claude/rules/challenge-idempotency.md` reference scenario).
- [x] 8.4 `IdempotencyConcurrentIT` — N concurrent duplicates of the same transaction id, exactly one
      `APPLIED`, the rest `REPLAYED`.
- [x] 8.5 `IdempotencyGateDownFailOpenIT` — pauses (not stops, to keep the shared static container
      usable by later test classes) the Valkey container; exactly-once still holds via
      `processed_transaction`, and calls return within a bounded time (proves the fail-fast wiring, not
      just eventual correctness).
- [x] 8.6 `TransactionIdConflictIT` — same transaction id, two *disjoint* accounts (the only way to
      reach the DB-level constraint guard rather than the app-level `processed.find` replay check, which
      only ever sees a row committed under a lock it shares) → `409 TRANSACTION_ID_CONFLICT`, not a
      leaked exception.
- [x] 8.7 `./mvnw test` with Docker running. All 6 tests green.

## 9. Messaging: outbox appender, integration-event contract, Kafka publisher

- [x] 9.1 Added `spring-boot-starter-jdbc`, `spring-boot-starter-kafka`, `spring-boot-starter-json`,
      `mapstruct`, `lombok` to `adapters/driven/messaging/pom.xml`, plus its own
      `maven-compiler-plugin` `annotationProcessorPaths` override; corrected the module's stale
      "structural placeholder" description.
- [x] 9.2 `IntegrationEventEnvelope` + versioned `...V1` payload records
      (`AccountOpenedV1`/`AccountCreditedV1`/`AccountDebitedV1`/`TransactionPostedV1`/
      `TransactionCompensatedV1`) — amounts as decimal strings, messaging-owned Jackson 3
      (`tools.jackson`) `ObjectMapper`.
- [x] 9.3 `IntegrationEventFactory` — exhaustive `DomainEvent → IntegrationEventEnvelope` pattern-match
      dispatch, throws on an unmapped event type. Completeness proven in `architecture-tests`
      (`IntegrationEventFactoryCompletenessTest`, not `messaging` itself — needed ArchUnit's class
      importer to enumerate every concrete `AbstractDomainEvent` subclass across the whole domain
      package and cross-check against this test's own fixture list, which `messaging` alone can't see).
- [x] 9.4 `JdbcOutboxAppender` — one INSERT per event, `CAST(? AS jsonb)`, reads the current
      correlation id from MDC (falls back to `NULL`, never synthesizes), computes topic + partition
      key at append time.
- [x] 9.5 Unit tests: `IntegrationEventFactoryCompletenessTest` (factory dispatch + completeness, see
      9.3). `JdbcOutboxAppender` has no meaningful unit test (needs a real Postgres transaction to
      prove anything) — its correctness is proven by the Testcontainers ITs in 9.8 instead.
- [x] 9.6 `OutboxPollingPublisher` — `FOR UPDATE SKIP LOCKED` claim, send-then-mark, exponential
      backoff via `next_attempt_at`, `max-attempts` alerting without deletion. `OutboxCleanupJob` —
      separate scheduled bean, chunked retention DELETE. `spring.task.scheduling.simple.concurrency-limit: 1`
      so poll and cleanup never overlap.
- [x] 9.7 Kafka producer config in `application.yaml` (`acks=all`, `enable.idempotence=true`,
      `max.in.flight=5`, `ByteArraySerializer`); topic/poll/backoff/retention as `taraz.outbox.*`
      `@ConfigurationProperties`.
- [x] 9.8 `RollbackLeavesNoOutboxRowIT` — already fully proven by `RollbackLeavesNoTraceIT` (task 7.7),
      which asserts zero rows in `outbox` (alongside `ledger_transaction`/`processed_transaction`) after
      an insufficient-funds rollback — no separate file, to avoid a duplicate test asserting the same
      thing against the same table. `OutboxPublishedExactlyOncePerOccurrenceIT` (real `KafkaConsumer`,
      dedup by event id via the `X-Event-Id` header, asserts partition key = aggregate id per ADR-0051),
      `CorrelationPropagationIT` (two cases: correlation id present reaches both the outbox row's
      `correlation_id` column and the `kafka_correlationId` Kafka header; absent correlation id leaves
      both null/omitted, never synthesized).
- [x] 9.9 `./mvnw test` with Docker running. All green.

### 9a. Scope amendment — ShedLock for outbox scheduler coordination (requested by user during apply)

- [x] 9a.1 Added ShedLock (`net.javacrumbs.shedlock:shedlock-spring` + `shedlock-provider-jdbc-template`,
      `7.8.0`, pulled via `ctx7` docs per `.claude/rules/context7.md` rather than from memory) so the
      outbox poller and cleanup job coordinate correctly across horizontally-scaled pods, resolving
      ADR-0055's originally-accepted "single-instance poller" limitation instead of leaving it as a
      documented non-goal. New ADR-0057 (references design.md's scope; supersedes-in-part ADR-0055's
      caveat via its status line, not a rewrite of its decision text). `shedlock` table added as
      `messaging`'s own migration (`0007-shedlock.sql`, column shape exactly matching ShedLock's own
      JDBC-provider contract). `@EnableSchedulerLock` + `JdbcTemplateLockProvider(.usingDbTime())` in
      `OutboxPublisherConfiguration`; `@SchedulerLock(name = "outbox-poll", lockAtMostFor = "30s")` /
      `@SchedulerLock(name = "outbox-cleanup", lockAtMostFor = "10m")` on the two scheduled methods,
      each with `LockAssert.assertLocked()`. Found and fixed: ShedLock's method-level AOP interceptor
      (like the `@Repository` exception-translation proxy in task group 5/6) needs CGLIB, so
      `OutboxPollingPublisher`/`OutboxCleanupJob` can't be `final` either.
- [x] 9a.2 **Found and fixed two real, pre-existing correctness bugs while proving the full app
      actually boots** (a `ContextLoadsIT` smoke test, added ahead of the planned group-7 IT suite
      since none of it can run until every port has a bean anyway):
      1. **`spring-boot-liquibase` (Boot 4's modular Liquibase *autoconfiguration* artifact) was never
         a dependency** — only the raw `liquibase-core` library was declared in `container/pom.xml`.
         Migrations were silently never run through the Spring-managed application at all; only the
         Testcontainers-direct path in `SchemaIT` ever exercised Liquibase. Added the dependency.
      2. **`container/src/test/resources/application.yaml` shadowed, rather than merged with,** the
         main `application.yaml` on the test classpath (Spring Boot loads only the first same-named
         `application.yaml` it finds — it does not merge two files with that name from different
         classpath roots). Every integration test up to this point ran with zero datasource/liquibase/
         redis/kafka configuration from the main file. Renamed to `application-test.yaml` and added
         `@ActiveProfiles("test")` to `TarazIntegrationTest`, which correctly layers a profile-specific
         file on top of the base one. A stale `target/test-classes/application.yaml` compiled before
         the rename also had to be removed by hand (`mvn clean`) — Maven's resource copy does not
         delete outputs for since-renamed sources.
      Both were completely invisible until a real `@SpringBootTest` was attempted — neither `SchemaIT`
      (deliberately Spring-context-free) nor any unit test could have caught them.
- [x] 9a.3 `ContextLoadsIT` (Testcontainers, Postgres only — Valkey/Kafka connection factories are
      lazy) added to `container/src/test` proving the full application context boots with all seven
      outbound ports satisfied by real beans, for the first time in this project's history.

## 10. Correlation constant promotion and architecture enforcement

- [x] 10.1 **Scope amendment (requested by user mid-apply):** renamed the correlation header from
      ADR-0043's `X-Flow-ID` to the more widely recognized `X-Correlation-ID`, and standardized the
      Kafka header to Spring Kafka's own `kafka_correlationId` constant rather than reusing the HTTP
      header name — recorded as new ADR-0056 (amends ADR-0043's status line, does not edit its
      decision text). Renamed `FlowIdFilter` → `CorrelationIdFilter`, `RestHeaders.X_FLOW_ID` →
      `X_CORRELATION_ID`, MDC key `flow_id` → `correlation_id` (promoted straight to a public constant
      on `RestHeaders`, `CORRELATION_ID_MDC_KEY` — folding in what this task originally planned),
      `MessagingCorrelation.FLOW_ID_MDC_KEY` → `CORRELATION_ID_MDC_KEY`, outbox column `flow_id` →
      `correlation_id`, and updated `AccountControllerTest` accordingly.
- [x] 10.2 Add `logging.pattern.level` to `application.yaml` so `%X{correlation_id}` actually appears in log
      output — confirmed live in every IT run's log lines (`[taraz,correlation_id=...]`).
- [x] 10.3 Added `driven_adapters_do_not_use_application_service`, `persistence_does_not_depend_on_messaging`,
      `messaging_does_not_depend_on_persistence` to `architecture-tests/.../LayerBoundariesTest.java`, and
      `CorrelationIdMdcKeyConstantsAgreeTest` asserting `RestHeaders.CORRELATION_ID_MDC_KEY.equals(
      MessagingCorrelation.CORRELATION_ID_MDC_KEY)`.
- [x] 10.4 `./mvnw test`.

### 10a. Scope amendment — REST JSON naming: snake_case → camelCase (requested by user during apply)

- [x] 10a.1 `spring.jackson.property-naming-strategy` switched `SNAKE_CASE` → `LOWER_CAMEL_CASE` in
      `application.yaml` — recorded as new ADR-0058 (amends ADR-0043's status line, does not edit its
      decision text; a fifteenth new ADR for this change). REST DTO Java fields were already camelCase,
      so this removes a translation layer rather than adding one. The Kafka event contract
      (`IntegrationEventEnvelope`) is unaffected — it always serialized with its own
      `messaging`-owned `ObjectMapper`, independent of the Spring `spring.jackson.*` bean, and was
      already camelCase.
- [x] 10a.2 Updated `AccountControllerTest`, `AccountOperationsControllerTest`, `TransferControllerTest`
      (their own `spring.jackson.property-naming-strategy` test-property overrides and every
      `account_id`/`transaction_id`/`source_account_id`/`destination_account_id` jsonPath assertion and
      request-body literal), the `rest-api` spec delta (new `JSON contract conventions` MODIFIED block),
      and the `dto` package-info javadoc.
      Verified live end-to-end via `just up` + `just run` + curl: response bodies now render
      `accountId`/`transactionId`.
- [x] 10a.3 `./mvnw test` — REST module (24 tests) and full suite both green.

## 11. Project-scaffolding and build/CI corrections

- [x] 11.1 Add JaCoCo to root `pom.xml` (the README already carries SonarCloud coverage badges with
      no coverage ever produced today).
- [x] 11.2 Confirmed `.github/workflows/ci.yml`'s `-Pci verify` run passes
      `-Dtaraz.require.docker=true` (design.md D11/ADR-0053) so a Docker-less CI run fails loudly (the
      `ci` Maven profile sets the property; the workflow invokes `./mvnw -B -Pci verify`).
- [x] 11.3 Added `.env.example` entry for `KAFKA_BOOTSTRAP_SERVERS`.
- [x] 11.4 Rewrote `README.md`'s status section: removed the "real adapters have not yet replaced the
      fakes" language; documented the Valkey read-through / Postgres authoritative idempotency design
      (including the disjoint-account `TRANSACTION_ID_CONFLICT` guard), the real `SELECT ... FOR UPDATE`
      deadlock-free proof now backed by Testcontainers, the connection-pool backpressure design
      (ADR-0054), ShedLock, and correlation propagation, per the challenge's required README sections
      (`.claude/rules/docs-fa.md`).
- [x] 11.5 `./mvnw test` green (see group 12.4 for the full final run).

## 12a. k6 load test (scope amendment — requested by user during apply, reverses proposal.md's stated non-goal)

- [ ] 12a.1 `git pull` after committing this change's work (user confirmed a k6 harness already
      exists on `origin` from other work) — inspect what landed before writing anything new. Only add
      a script/justfile recipe/README section for whatever gap remains after the pull.
- [ ] 12a.2 Boot real infra (`just up`) and the application (`just run` or equivalent), run the
      existing (or gap-filled) k6 script against it end to end, confirm both the application and k6
      itself work (script exits 0, thresholds pass, final-balance assertion holds — not merely "no
      errors"), then tear down cleanly.

## 12. Final verification

- [x] 12.1 `just up && just run`: application boots against real compose infra in 9.67s.
- [x] 12.2 Manual smoke test: `POST /accounts` → `POST .../credits` (with `Idempotency-Key`) →
      `GET .../balance` round-trips; transfer round-trips atomically (700/300 split confirmed);
      `amount <= 0` → 400 `INVALID_AMOUNT`; unknown account → 404 `ACCOUNT_NOT_FOUND`; same account +
      same key + different amount → 201 `REPLAYED` (app-level idempotency, correct per design — the
      409 conflict path is unreachable from sequential single-threaded calls, only from a genuine race
      between disjoint-account operations, already proven by `TransactionIdConflictIT`).
      **Found and fixed a real, pre-existing REST-layer defect while smoke-testing**: `Money` balances
      serialized as scientific notation (e.g. `1E+3` for 1000) — Jackson's default `BigDecimal#toString`
      for round `Money` amounts, valid JSON but wrong for a financial API. Fixed with
      `spring.jackson.write.write-bigdecimal-as-plain: true` in `application.yaml`; verified balances now
      render as plain `1000`.
- [x] 12.3 Confirmed via `kafka-console-consumer` on both topics: `taraz.account.v1` carries
      `account.opened`/`account.credited` with `kafka_correlationId` matching the originating request's
      `X-Correlation-ID` (or an auto-generated one when the request carried none — never empty);
      `taraz.transaction.v1` carries `transaction.posted` for both CREDIT and TRANSFER, same header
      behavior. Amounts on the wire are decimal strings, never JSON numbers (ADR-0050).
- [x] 12.4 Full `./mvnw test` run (Docker present) green end to end — run four times back-to-back
      (twice default profile, twice `-Pci verify`) to shake out flakiness; found and fixed a real
      Kafka-consumer-side flake in `CorrelationPropagationIT`/`OutboxPublishedExactlyOncePerOccurrenceIT`
      under full-suite load (see `TestKafkaConsumers` — subscribe-from-latest instead of draining a
      multi-thousand-record backlog from earlier heavy tests, plus an early-exit `pollUntilFound`), and a
      real throughput bug in `OutboxPollingPublisher` (sends within a batch were issued and awaited
      sequentially — one slow row could stall an entire 128-row batch for up to `128 × send-timeout`;
      fixed to issue all sends up front, await after). The Docker-absent clean-skip path
      (`@Testcontainers(disabledWithoutDocker = true)` + surefire `*IT.java` includes) was verified
      earlier in task 3.5 and is unchanged by anything in this session since — not re-verified live here
      to avoid disrupting the Docker daemon mid-session.
