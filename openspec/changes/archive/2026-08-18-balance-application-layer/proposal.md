## Why

`core/domain` is complete and proven (Account/Transaction aggregates, Money, PostingService, Specifications — commit `6457415`), but nothing above it exists: `core/application/{port,service,query}` hold only `package-info.java` markers, and the challenge's mandated `BalanceService` contract (`credit`/`debit`/`transfer`/`getBalance`) exists nowhere in the repository. Without this layer there is no way to invoke the domain at all — no command, no use case, no idempotency guarantee, no transaction boundary. This change builds it now so the project has a complete, testable, callable vertical slice before any adapter (REST, persistence, messaging) is written.

## What Changes

- Add CQRS write-side use cases for credit, debit, and transfer as per-use-case inbound ports (`CreditUseCase`, `DebitUseCase`, `TransferUseCase`), each with an immutable, jakarta-validated command record (ADR-0007, ADR-0034).
- Add a `BalanceService` facade in `ports.inbound` carrying the challenge's exact required signatures, delegating to the three use cases and the read side; the one place a `Result` failure becomes a thrown `BalanceOperationException`.
- Add the CQRS read side: `GetBalanceUseCase` / `GetBalanceQuery` / `BalanceView`, a stateless handler in `core/application/query`, and its own `AccountBalanceReadRepository` outbound port — never routed through the write-side service (ADR-0007, ADR-0033).
- Add every outbound port the write side needs: `AccountRepository` (row-lock ordering owned by the port, ADR-0026), `TransactionRepository`, `ProcessedTransactionStore`, `OutboxAppender`, `IdempotencyGate` (with a sealed `GateDecision`), and `UnitOfWork` — the mechanism that opens the ADR-0018 transaction boundary from framework-free `core.application.service` code.
- Guarantee idempotency in the handler layer per ADR-0021/0034: a fast advisory Valkey-backed gate ahead of the transaction, and an authoritative lock-then-check against `ProcessedTransactionStore` inside it, so a duplicate — sequential or concurrent — never has a second effect on a balance.
- Guarantee transfer atomicity and deadlock-freedom per ADR-0026/0037: both account rows locked in one canonical ascending order inside a single transaction alongside the `Transaction` aggregate and outbox rows.
- Reject `transfer(A, A, …)` before any lock or transaction is opened, and without consuming the `transactionId` (ADR-0028).
- Fix real gaps the domain didn't need until now: an `ACCOUNT_NOT_FOUND` / `INVALID_ACCOUNT_ID` error code, a validating `AccountId.of(String)` factory, a canonical `AccountId` ordering that matches PostgreSQL's `uuid` byte order (not `UUID.compareTo`'s signed-long order), and a `DistinctTransferAccountsSpecification` usable standalone before any `Transaction` is built.
- Narrow the ArchUnit "no Spring in core" rule from `..core..` to `..core.domain..`, and permit `@Service`/constructor injection (not `@Transactional`, not Spring Data) in `core/application`.

**Non-goals**

- No REST controllers, no DTO↔command MapStruct mapping (`adapters/driving/rest`).
- No JPA entities, no Liquibase changesets, no real `SELECT … FOR UPDATE` repository implementation (`adapters/driven/persistence`).
- No Valkey client implementation of `IdempotencyGate`, no Kafka client, no outbox polling publisher (`adapters/driven/messaging`).
- No compensate handlers (ADR-0035) — deferred to its own change; only the invariant that this layer's contracts don't foreclose it.
- No Testcontainers integration tests and no k6 load tests — this change proves handler logic against in-memory fakes; the real row-lock and unique-constraint proof arrives with the persistence change.

## Capabilities

### New Capabilities

- `balance-application-layer`: commands, command handlers (write side), queries (read side), and every inbound/outbound port for credit/debit/transfer/getBalance — atomicity, idempotency, and validation as guaranteed at the application boundary.

### Modified Capabilities

- `balance-domain-model`: adds `ACCOUNT_NOT_FOUND` and `INVALID_ACCOUNT_ID` to the error-code catalog, adds a validating `AccountId.of` factory, defines a canonical total ordering on `AccountId`, and makes the same-account-transfer rule checkable standalone before any `Transaction` is constructed.

## Impact

- **New code**: `core/application/port`, `core/application/service`, `core/application/query` (currently empty placeholders).
- **Modified code**: `core/domain/.../common/ErrorCode.java`, `core/domain/.../account/AccountId.java`, `core/domain/.../transaction/spec/DistinctTransferAccountsSpecification.java`.
- **Build/architecture**: `core/application/{port,service,query}/pom.xml` gain `jakarta.validation-api` (+ `spring-context` for service/query); `architecture-tests/.../LayerBoundariesTest.java` rules narrowed/extended.
- **Documentation**: four new ADRs (Spring-in-application-layer, UnitOfWork port, Postgres-authoritative idempotency, AccountId ordering); README sections on architecture/concurrency/idempotency/transfer updated from "design decision, not yet implemented" to implemented, with the stated honest gap (no Testcontainers proof yet).
- **No impact** on `adapters/*`, `container/*`, or the database schema — all remain structural placeholders.
