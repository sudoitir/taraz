## 1. Domain fixes

- [x] 1.1 Add `ACCOUNT_NOT_FOUND` and `INVALID_ACCOUNT_ID` to `core/domain/.../common/ErrorCode.java`
- [x] 1.2 Add validating factory `AccountId.of(@Nullable String)` returning `Result<AccountId>` with `INVALID_ACCOUNT_ID`, mirroring `TransactionId.of`; unit test for blank/null/valid input
- [x] 1.3 Implement `AccountId implements Comparable<AccountId>` using unsigned lexicographic byte comparison (not `UUID.compareTo`); unit test asserting agreement with PostgreSQL `uuid` ordering semantics on ids straddling the sign boundary, plus antisymmetry/transitivity/reflexivity
- [x] 1.4 Extract `TransferParties(AccountId source, AccountId destination)` record and a standalone specification over it in `core/domain/.../transaction/spec/`; have `DistinctTransferAccountsSpecification` (used inside `Transaction.transfer`) delegate to it so the rule has one definition and two call sites
- [x] 1.5 Run `./mvnw -pl core/domain test` — confirm all existing + new domain tests pass

## 2. New ADRs

- [x] 2.1 `docs/adr/0039-spring-in-application-layer.md` — Spring stereotype/DI allowed in `core/application`, forbidden in `core/domain` (Persian, RTL, per `.claude/rules/docs-fa.md` and `000-template.md`)
- [x] 2.2 `docs/adr/0040-unit-of-work-port.md` — `UnitOfWork` outbound port as the ADR-0018 boundary mechanism
- [x] 2.3 `docs/adr/0041-postgres-authoritative-idempotency.md` — Postgres authoritative, Valkey advisory/fail-open, including the crash/failure matrix
- [x] 2.4 `docs/adr/0042-account-id-ordering.md` — canonical `AccountId` ordering shared by Java and SQL, as ADR-0026's deadlock-freedom basis

## 3. `core/application/port` — inbound contracts

- [x] 3.1 Add `jakarta.validation:jakarta.validation-api` to `core/application/port/pom.xml`
- [x] 3.2 Add `OutcomeStatus` enum (`APPLIED`, `REPLAYED`), `AccountBalance` record, `CommandOutcome` record in `ports` root package
- [x] 3.3 Add `CreditCommand`, `DebitCommand`, `TransferCommand` records in `ports.inbound` with jakarta validation annotations (`@NotBlank`, `@Positive`), no timestamp field
- [x] 3.4 Add `CreditUseCase`, `DebitUseCase`, `TransferUseCase` interfaces (`Result<CommandOutcome> handle(...)`)
- [x] 3.5 Add `GetBalanceQuery` record, `BalanceView` record (carrying `AccountId`/`Money`), `GetBalanceUseCase` interface
- [x] 3.6 Add `BalanceService` interface with the challenge's exact signatures, and `BalanceOperationException` carrying a `DomainError`

## 4. `core/application/port` — outbound contracts

- [x] 4.1 Add `UnitOfWork` (`<T> Result<T> inTransaction(Supplier<Result<T>> work)`)
- [x] 4.2 Add `AccountRepository` (`Result<List<Account>> lockAllInIdOrder(Collection<AccountId>)`, `void saveAll(List<Account>)`) — Javadoc states it owns the canonical ordering
- [x] 4.3 Add `TransactionRepository` (`void save(Transaction)`), `ProcessedTransactionStore` (`Optional<CommandOutcome> find(TransactionId)`, `void record(TransactionId, CommandOutcome)`)
- [x] 4.4 Add `OutboxAppender` (`void append(List<DomainEvent>)`), `AccountBalanceReadRepository` (`Optional<BalanceView> findByAccountId(AccountId)`)
- [x] 4.5 Add sealed `GateDecision` (`Won`, `AlreadyApplied(CommandOutcome)`, `Unknown`) and `IdempotencyGate` (`tryBegin`, `publishOutcome`, `release`) — Javadoc states `tryBegin` MUST fail open to `Unknown`
- [x] 4.6 Run `./mvnw -pl core/application/port test` (compiles; no logic to test yet — interfaces only)

## 5. `core/application/service` — support + credit handler

- [x] 5.1 Add `jakarta.validation-api` and `spring-context` to `core/application/service/pom.xml`; correct the module's `<description>` (currently claims "framework-free")
- [x] 5.2 Add `support/CommandValidator` — runs `jakarta.validation.Validator`, maps `ConstraintViolation` property paths to `ErrorCode` (`amount→INVALID_AMOUNT`, `transactionId→INVALID_TRANSACTION_ID`, `*accountId→INVALID_ACCOUNT_ID`), returns `Result<Command>`
- [x] 5.3 Add `credit/CreditIntent` — factory `from(CreditCommand, CommandValidator)` producing `Result<CreditIntent>` via domain factories (`AccountId.of`, `Money.operationAmount`, `TransactionId.of`)
- [x] 5.4 Add `credit/CreditHandler implements CreditUseCase`, `@Service`, constructor-injected with `PostingService`, `Clock`, `UnitOfWork`, `AccountRepository`, `TransactionRepository`, `ProcessedTransactionStore`, `OutboxAppender`, `IdempotencyGate` — implements the full flow from design.md's atomic-unit sequence
- [x] 5.5 Unit tests: happy path; unknown account; invalid amount; blank transaction id — fakes (`FakeAccountRepository`, `FakeUnitOfWork`, `FakeTransactionRepository`, `FakeProcessedTransactionStore`, `FakeOutboxAppender`, `FakeIdempotencyGate`) written now and reused for Task 9, plus `support/CommandOutcomes` shared helper and `support/TestValidator` test helper

## 6. `core/application/service` — debit and transfer handlers

- [x] 6.1 Add `debit/DebitIntent`, `debit/DebitHandler implements DebitUseCase` — same shape as credit; insufficient-funds path returns `INSUFFICIENT_FUNDS` with nothing mutated
- [x] 6.2 Add `transfer/TransferIntent` — factory includes the standalone same-account check (Task 1.4) *before* gate/lock, per ADR-0028
- [x] 6.3 Add `transfer/TransferHandler implements TransferUseCase` — locks both accounts via `AccountRepository.lockAllInIdOrder`, no direction-dependent ordering
- [x] 6.4 Unit tests: successful transfer; insufficient source funds (both balances unchanged); same-account transfer rejected pre-lock with txId still usable afterward; unknown-account variants for both handlers

## 7. `core/application/service` — BalanceService facade

- [x] 7.1 Add `BalanceServiceFacade implements BalanceService`, `@Service`, delegating to the four use cases + `GetBalanceUseCase`; `Result` failures mapped to `BalanceOperationException`
- [x] 7.2 Implement `getBalance` narrowing `Money` via `longValueExact()`; Javadoc documents the overflow behavior (throws, does not truncate)
- [x] 7.3 Unit tests: each of the four methods happy-path and failure-path; overflow case for `getBalance`

## 8. `core/application/query` — read side

- [x] 8.1 Add `spring-context` to `core/application/query/pom.xml` (stereotype only)
- [x] 8.2 Add `balance/GetBalanceHandler implements GetBalanceUseCase`, `@Service`, stateless, reads through `AccountBalanceReadRepository` only — no `UnitOfWork`, no lock, no mutation
- [x] 8.3 Unit tests: existing account returns balance; unknown account returns `ACCOUNT_NOT_FOUND`; malformed id returns `INVALID_ACCOUNT_ID` before lookup; no write-side port reachable (structural, per constructor signature)

## 9. Test fakes and full idempotency suite

- [x] 9.1 `FakeUnitOfWork` — runs the supplier directly and releases `FakeAccountRepository`'s row locks for the calling thread when the work ends (success, failure, or exception); tracks `lastRolledBack` for assertions
- [x] 9.2 `FakeAccountRepository` — one `ReentrantLock` per account, `lockAllInIdOrder` acquires in ascending `AccountId` order and hands back fresh `Account` instances so in-place mutation is invisible until `saveAll`
- [x] 9.3 `FakeTransactionRepository`, `FakeProcessedTransactionStore`, `FakeOutboxAppender`
- [x] 9.4 `FakeIdempotencyGate` with three modes: `NORMAL` (map-backed `AlreadyApplied`/`Won`/`Unknown`), `ALWAYS_UNKNOWN`, `THROWS`
- [x] 9.5 `IdempotencySuiteTest`: 3× sequential credit/debit/transfer with one txId → single effect, replays report `REPLAYED` with the original balances; 50-thread `CyclicBarrier`-started concurrent duplicates (credit and transfer) → exactly one `APPLIED`; repeat under `ALWAYS_UNKNOWN` gate → still exactly one effect (proves D7). Found and fixed a real bug in the process: replay paths were returning the stored `CommandOutcome` verbatim (status `APPLIED` from when it was first recorded) instead of re-tagging it `REPLAYED` for the call that found it — added `CommandOutcomes.asReplay` and wired it into all three handlers' gate-hit and store-hit replay paths

## 10. Concurrency suite

- [x] 10.1 Single-account: 1000 barrier-started concurrent debits (100,000 → 0), assert exact final balance
- [x] 10.2 The challenge's canonical case: two concurrent `debit(A, 700)` on balance 1000 → exactly one succeeds, final balance 300
- [x] 10.3 Multi-account: two threads lock different accounts and rendezvous at a 2-party barrier while still holding their locks — a shared lock would deadlock this, independent locks complete within the timeout
- [x] 10.4 Bidirectional transfer stress: 200 concurrent transfers (100 each direction, unique txIds) between the same two accounts — all complete (no deadlock), total balance conserved

## 11. Architecture enforcement

- [x] 11.1 Renamed `core_does_not_depend_on_spring` to `domain_does_not_depend_on_spring`, narrowed from `..core..` to `..core.domain..` in `LayerBoundariesTest`
- [x] 11.2 Add rule: `..core.application..` depends on no `org.springframework..` package other than `org.springframework.stereotype..` and `org.springframework.beans.factory..`
- [x] 11.3 Add rule: `..core.application.query..` never depends on a write-side-only outbound port (everything in the outbound-ports package except `AccountBalanceReadRepository`) nor on `..core.application.service..`
- [x] 11.4 Add rule: no class anywhere in `..core..` calls `Instant.now()`
- [x] 11.5 Add rule: `..core.application.ports..` contains only interfaces, records, enums, and `Throwable` subtypes (`BalanceOperationException`) — package-info excluded, no other concrete classes
- [x] 11.6 Run `./mvnw -pl architecture-tests -am test` — all 17 rules green

## 12. Documentation and housekeeping

- [x] 12.1 Update README.md (Persian) — Architecture, Concurrency, Idempotency, Transfer, same-account sections rewritten to describe what's implemented, including the honest fake-vs-real-lock testing gap
- [x] 12.2 Update README.md — Implementation status section: application layer marked implemented, remaining items reordered around real adapters/compensate/Testcontainers/k6
- [x] 12.3 Fix `CLAUDE.md` and `openspec/config.yaml` — replaced stale "docs/adr/0001–0023" with a reference to the directory
- [x] 12.4 Run `./mvnw test` (115 tests, all modules) and `./mvnw -B -Pci verify` — both green, Docker-free
- [x] 12.5 Run `just format` — no diff beyond what was already staged
