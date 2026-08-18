# Tasks: REST Driving Adapter + CreateAccount

## 1. ADR

- [x] 1.1 Write `docs/adr/0043-rest-contract-details.md` (Persian, `<div dir="rtl">`, Nygard template): Idempotency-Key ↔ transactionId mapping, `Idempotency-Replayed: true` on replays, replay returns same 201 + Location, RFC 7807 via Spring ProblemDetail (no Zalando problems library), snake_case JSON, `X-Flow-ID` correlation, `Cache-Control: no-store` on balance reads. Status پذیرفته‌شده. References ADR-0008/0006/0031/0033.

## 2. CreateAccount use case (core)

- [x] 2.1 Test: `CreateAccountHandlerTest` in `core/application/service` using existing fakes (`FakeUnitOfWork`, `FakeAccountRepository`, `FakeOutboxAppender`) — opens with zero balance, unique id per call, saves, appends `AccountOpened`.
- [x] 2.2 `core/application/port/.../inbound/CreateAccountUseCase.java`: `Result<BalanceView> handle()`.
- [x] 2.3 `core/application/service/account/CreateAccountHandler.java` (+ `package-info`): `IdGenerator` → `Account.open(id, Money.ZERO, clock.instant())` → `unitOfWork.inTransaction` → `accounts.saveAll` → outbox append of pulled events. No gate, no processed-store.

## 3. REST adapter

- [x] 3.1 Slice tests first (`@WebMvcTest`, ports mocked): per endpoint — 201/200 + `Location` + snake_case body; replay → 201 + `Idempotency-Replayed: true`; each ErrorCode row → status + `application/problem+json` + `code`; missing `Idempotency-Key` → 400; malformed UUID → 400; unreadable body → 400; `X-Flow-ID` echo/generated; `Cache-Control: no-store` on balance read. (`Date` header assertion dropped: it is emitted by the servlet container, not visible to MockMvc slice tests.)
- [x] 3.2 DTO records: `CreditRequest`, `DebitRequest`, `TransferRequest`, `AccountResponse`, `CommandOutcomeResponse`, `BalanceEntry` (jakarta validation annotations).
- [x] 3.3 `RestMapper` (MapStruct, spring component model): DTO→command, `CommandOutcome`/`BalanceView`→response.
- [x] 3.4 `ProblemFactory` (Result failure → ProblemDetail, ErrorCode→status table) + `ProblemAdvice` (`@RestControllerAdvice` at `Ordered.HIGHEST_PRECEDENCE` — ahead of Boot 4's auto-configured `ProblemDetailsExceptionHandler` at `@Order(0)`: `MissingRequestHeaderException`, `MethodArgumentNotValidException`, `MethodArgumentTypeMismatchException`, `HttpMessageNotReadableException` → 400; fallback `Exception` → 500 opaque + log with flow id).
- [x] 3.5 `FlowIdFilter` (`OncePerRequestFilter`): read/generate `X-Flow-ID`, echo on response, MDC `flow_id`.
- [x] 3.6 Controllers (Lombok `@RequiredArgsConstructor`): `AccountController` (POST /accounts, GET balance), `AccountOperationsController` (credits, debits), `TransferController`. Read `Idempotency-Key` via `@RequestHeader`; set `Location`, `Idempotency-Replayed`, `Cache-Control` via `ResponseEntity`.

## 4. Wiring & config

- [x] 4.1 `container/application.yaml`: `spring.jackson.property-naming-strategy=SNAKE_CASE`, `spring.mvc.problemdetails.enabled=true`.
- [x] 4.2 Verify `container` pom depends on the `rest` module; add if missing.
- [x] 4.3 ArchUnit: `LayerBoundariesTest.driving_adapters_do_not_use_domain` replaced with an allowlist rule — the driving adapter may touch only the port contract surface (`Result`(+nested), `DomainError`, `ErrorCode`, `Money`, `AccountId`, `TransactionId`).

## 5. Verify

- [x] 5.1 `./mvnw test` green across the reactor.
- [x] 5.2 `./mvnw spotless:check` clean (or `just` equivalent).
- [x] 5.3 Note honestly: boot smoke requires driven adapters (out of scope) — slice tests are the e2e of this change.
