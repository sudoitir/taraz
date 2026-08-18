# Design: REST Driving Adapter + CreateAccount

## Context

See proposal.md — Why. Current state: inbound ports return `Result<CommandOutcome>` / `Result<BalanceView>` (`core/application/port/.../inbound/`); `ErrorCode` is a stable enum; the `rest` module pom is fully wired; driven persistence/messaging adapters are not implemented (out of scope). Binding ADRs: 0006 (controllers are pure driving adapters), 0008 (Zalando, 201 + Location), 0031 (Lombok/MapStruct in adapters only), 0033 (reads go straight to `GetBalanceUseCase`), 0036 (money as minor units).

## Goals / Non-Goals

**Goals:** a complete, testable HTTP contract implemented with slice tests (`@WebMvcTest` + mocked ports); a minimal CreateAccount use case in core; ADR 0043 for the contract details.

**Non-Goals:** persistence/messaging adapters, boot-smoke wiring (context can't start until driven adapters land), OpenAPI spec generation, versioning prefix.

## Decisions

### D1 — Endpoint shape: domain resources

`POST /accounts`, `POST /accounts/{account-id}/credits`, `POST /accounts/{account-id}/debits`, `POST /transfers`, `GET /accounts/{account-id}/balance`. Alternatives rejected: single `/transactions` resource with a type discriminator (weaker typing, messier per-type validation); RPC-style `/credit` `/debit` `/transfer` (against Zalando resource orientation).

### D2 — Idempotency-Key maps to transactionId

The standard `Idempotency-Key` request header IS the command's `transactionId`. No separate concept: the header is the contract-friendly spelling of the domain identifier. Missing/blank → `400` with `INVALID_TRANSACTION_ID`. Replays return the **same** `201` + same `Location` as the original application (identical request → identical response), differentiated only by `Idempotency-Replayed: true`. Alternative rejected: `201`-then-`200` (breaks idempotency's same-response semantics).

### D3 — Problem Details via Spring built-in, not the Zalando problems library

`spring.mvc.problemdetails.enabled=true` + one `@RestControllerAdvice` (`ProblemAdvice`) + one `ProblemFactory` that folds `Result` failures into `ProblemDetail` responses. Domain failures travel as `Result`, not exceptions (ADR-0005/0011), so the advice only handles transport-level errors (missing header, validation, malformed UUID/body) plus a 500 fallback; domain mapping lives in the factory. Alternative rejected: `org.zalando:problem` + `problem-spring-web` — Spring 6+ covers RFC 7807 natively; the extra library buys nothing (ponytail: platform first).

Status mapping: `INVALID_AMOUNT`/`INVALID_ACCOUNT_ID`/`INVALID_TRANSACTION_ID` → 400; `ACCOUNT_NOT_FOUND` → 404; `INSUFFICIENT_FUNDS`/`SAME_ACCOUNT_TRANSFER` → 422; internal invariants (`NEGATIVE_BALANCE`, `UNBALANCED_TRANSACTION`, `INVALID_ENTRY_SHAPE`, `COMPENSATION_TARGET_NOT_APPLIED`) → 500 without leaking detail. Every problem body carries extension member `code` and a stable `type` URN `urn:taraz:problem:<kebab-code>`.

### D4 — X-Flow-ID via OncePerRequestFilter

`FlowIdFilter`: read `X-Flow-ID` or generate a UUID, echo on every response (including errors — the filter wraps everything), bind to SLF4J MDC as `flow_id`. Alternative rejected: reading the header per-controller (error responses would lose it).

### D5 — DTOs as records, one MapStruct mapper

Records `CreditRequest`, `DebitRequest`, `TransferRequest`, `AccountResponse`, `CommandOutcomeResponse`, `BalanceEntry`; one `RestMapper` (`componentModel = "spring"`) maps DTO→command and `CommandOutcome`/`BalanceView`→response. `Money`↔`BigDecimal` is a 1:1 accessor mapping. Controllers use Lombok `@RequiredArgsConstructor`. Jackson `SNAKE_CASE` naming strategy configured in `container/application.yaml`.

### D6 — CreateAccount: minimal handler, no command record, returns BalanceView

`CreateAccountUseCase.handle()` takes no input: the handler generates `AccountId` via the domain `IdGenerator` (UUIDv7, ADR-0016/0038), calls `Account.open(id, Money.ZERO, clock.instant())`, persists via `AccountRepository.saveAll` inside `UnitOfWork.inTransaction`, appends pulled `AccountOpened` events to `OutboxAppender`, and returns `Result<BalanceView>` (reusing the existing view). Alternative rejected: client-supplied account id (server-generated ids are the Zalando POST default; client-supplied ids can be added later without breaking the contract).

### D7 — Controller granularity: three controllers

`AccountController` (POST /accounts, GET balance), `AccountOperationsController` (credits + debits — same path root, same shape), `TransferController`. One controller per resource root keeps files small without fragmenting trivially-related operations.

## Risks / Trade-offs

- [Money narrowing: `CommandOutcome` balances are `Money` (BigDecimal), wire sends a JSON number] → mapper serializes `minorUnits()` directly; BigDecimal→number is lossless for whole minor units, and JSON round-trip to `long` stays within the challenge's range. Overflow beyond `long` is a domain-acknowledged non-case at this boundary (BalanceServiceFacade already narrows with `longValueExact`).
- [`Idempotency-Key` as transactionId means a key replayed against a *different* endpoint/body is still "the same transaction"] → consistent with ADR-0041's authoritative-idempotency model; documented in ADR 0043 as the client's responsibility.
- [Slice tests mock the ports, so they can't catch a controller calling an outbound port] → ArchUnit boundary rule covers it (`LayerBoundariesTest` extension).
- [500 for internal invariants hides diagnostics] → server-side log with `X-Flow-ID` correlation; response stays opaque.

## Open Questions

None.
