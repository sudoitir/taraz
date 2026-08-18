# Proposal: REST Driving Adapter (Zalando) + CreateAccount Use Case

## Why

The challenge's minimum contract (`BalanceService`) is fully implemented in core, but nothing can drive the service over HTTP: no e2e testing, no black-box judging, no k6 scenario is possible (ADR-0008, ADR-0022). ADR-0008 (پذیرفته‌شده) mandates a REST API following the Zalando RESTful API Guidelines; the `adapters/driving/rest` module exists with its build fully wired (Spring MVC, bean validation, MapStruct, Lombok, NullAway) but contains only a `package-info`. Additionally, operating on an account that cannot be created through the API makes the service unusable end-to-end — every credit/debit/transfer on a fresh deployment fails with `ACCOUNT_NOT_FOUND`.

## What Changes

- **New core use case** `CreateAccountUseCase` (inbound port + handler in `core/application`): opens an account with a server-generated UUIDv7 id and zero balance via the existing `Account.open` factory, persists through `AccountRepository.saveAll`, and appends `AccountOpened` domain events to the outbox — consistent with ADR-0009/0010. Not a financial transaction: no `IdempotencyGate`, no `ProcessedTransactionStore`.
- **REST driving adapter** (`adapters/driving/rest`) implementing ADR-0008:
  - `POST /accounts` → 201 + `Location: /accounts/{id}`
  - `POST /accounts/{account-id}/credits` / `.../debits` → 201 + `Location`
  - `POST /transfers` → 201 + `Location`
  - `GET /accounts/{account-id}/balance` → 200
  - Controllers call inbound ports (`CreditUseCase`, `DebitUseCase`, `TransferUseCase`, `GetBalanceUseCase`, `CreateAccountUseCase`) directly — never `BalanceServiceFacade`, never outbound ports (ADR-0006).
- **Headers**: `Idempotency-Key` request header maps to the command's `transactionId` (required on credit/debit/transfer); `Idempotency-Replayed: true` response header on replays; `X-Flow-ID` correlation (echo or generate, MDC-bound); `Cache-Control: no-store` on balance reads; replayed commands return the same `201` + same `Location` as the original.
- **Errors**: RFC 7807 Problem Details via Spring's built-in `ProblemDetail` + one `@RestControllerAdvice`; stable `code` extension member carrying the `ErrorCode` name; mapping table 400/404/422/500.
- **JSON**: snake_case field names (Zalando), amounts as minor-unit numbers (ADR-0036).
- **DTO mapping** via MapStruct, boilerplate via Lombok — adapters only, per ADR-0031.
- **New ADR 0043** recording the REST contract details (header semantics, replay status code, problem-details choice).

## Capabilities

### New Capabilities

- `rest-api`: HTTP contract of the balance service — endpoints, request/response shapes, headers (idempotency, correlation), and error mapping per Zalando guidelines.

### Modified Capabilities

- `balance-application-layer`: adds the `CreateAccount` use case requirement (open account with generated UUIDv7 id, zero balance, `AccountOpened` event appended to outbox).

## Impact

- **New code**: `core/application/port` (`CreateAccountUseCase`), `core/application/service/account/` (`CreateAccountHandler`), `adapters/driving/rest` (controllers, DTO records, MapStruct mapper, `ProblemAdvice`, `FlowIdFilter`), `docs/adr/0043-rest-contract-details.md`.
- **Config**: `container/application.yaml` — Jackson snake_case, `spring.mvc.problemdetails.enabled=true`.
- **Tests**: `@WebMvcTest` slice tests per endpoint and error row; `CreateAccountHandler` unit tests with existing fakes; ArchUnit boundary check for the rest module.
- **Dependencies**: none added — pom already declares everything needed.
- **Non-goals**: driven persistence/messaging adapters (separate change), API versioning prefix (single version), rate limiting/security headers (no limiter, no Spring Security), `GET /transactions/{id}` (no such query port), `processed_at` timestamps in response bodies (ledger time is owned by core's `Clock`; `CommandOutcome` does not carry it — possible follow-up).
