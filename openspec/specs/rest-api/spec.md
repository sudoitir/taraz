# rest-api Specification

## Purpose

The HTTP contract of the balance service: how external clients open accounts, credit, debit, transfer, and read balances over REST — including idempotency and correlation headers, response shapes, and error mapping — per ADR-0008 (Zalando RESTful API Guidelines).

## Requirements

### Requirement: Account creation endpoint

The system SHALL expose `POST /accounts` which opens a new account with a server-generated identifier and zero balance, and SHALL respond `201 Created` with a `Location` header pointing at the created account resource.

#### Scenario: Successful account creation

- **WHEN** a client sends `POST /accounts`
- **THEN** the response is `201 Created` with `Location: /accounts/{account-id}`, the body contains the new `account_id` and a `balance` of `0`, and the identifier is unique per request

#### Scenario: Created account is immediately operable

- **WHEN** an account has been created via `POST /accounts`
- **THEN** `GET /accounts/{account-id}/balance` returns its zero balance and credit/debit/transfer operations on it are accepted

### Requirement: Credit and debit endpoints

The system SHALL expose `POST /accounts/{account-id}/credits` and `POST /accounts/{account-id}/debits`, each accepting a JSON body with a positive integer `amount` in minor units, and SHALL respond `201 Created` with a `Location` header pointing at the account's balance resource.

#### Scenario: Successful credit

- **WHEN** a client sends `POST /accounts/{account-id}/credits` with a valid `Idempotency-Key` and body `{"amount": 500}`
- **THEN** the response is `201 Created` with `Location: /accounts/{account-id}/balance` and a body containing `transaction_id`, `status: "APPLIED"`, and the resulting `balances` of the affected account

#### Scenario: Successful debit

- **WHEN** a client sends `POST /accounts/{account-id}/debits` with a valid `Idempotency-Key` and a body amount not exceeding the current balance
- **THEN** the response is `201 Created` with the same shape as a credit

#### Scenario: Non-positive amount rejected

- **WHEN** a credit or debit request body carries `amount <= 0`
- **THEN** the response is `400 Bad Request` in `application/problem+json` format, and no balance changes

### Requirement: Transfer endpoint

The system SHALL expose `POST /transfers` accepting `source_account_id`, `destination_account_id`, and a positive `amount`, and SHALL respond `201 Created` with a `Location` header pointing at the source account's balance resource.

#### Scenario: Successful transfer

- **WHEN** a client sends `POST /transfers` with a valid `Idempotency-Key`, two existing distinct accounts, and an amount not exceeding the source balance
- **THEN** the response is `201 Created` with `Location: /accounts/{source-account-id}/balance` and a body containing `transaction_id`, `status`, and the resulting `balances` of both accounts

#### Scenario: Same-account transfer rejected

- **WHEN** `source_account_id` equals `destination_account_id`
- **THEN** the response is `422 Unprocessable Content` with problem code `SAME_ACCOUNT_TRANSFER`, and both balances are unchanged

#### Scenario: Insufficient funds

- **WHEN** the transfer amount exceeds the source balance
- **THEN** the response is `422 Unprocessable Content` with problem code `INSUFFICIENT_FUNDS`, and both balances are unchanged

### Requirement: Balance read endpoint

The system SHALL expose `GET /accounts/{account-id}/balance` returning `200 OK` with the account's current balance, and SHALL mark the response `Cache-Control: no-store`.

#### Scenario: Read balance of an existing account

- **WHEN** a client sends `GET /accounts/{account-id}/balance` for an existing account
- **THEN** the response is `200 OK` with body `{account_id, balance}`, and the `Cache-Control: no-store` header is present

#### Scenario: Read balance of an unknown account

- **WHEN** the referenced account does not exist
- **THEN** the response is `404 Not Found` with problem code `ACCOUNT_NOT_FOUND`

### Requirement: Idempotency-Key header

Every `POST` to credits, debits, or transfers SHALL require an `Idempotency-Key` request header, which the system SHALL use as the operation's transaction identifier. A missing or blank header SHALL be rejected before any account lookup.

#### Scenario: Missing Idempotency-Key

- **WHEN** a credit, debit, or transfer request arrives without an `Idempotency-Key` header
- **THEN** the response is `400 Bad Request` with problem code `INVALID_TRANSACTION_ID`, and no balance changes

#### Scenario: Duplicate request returns the original outcome

- **WHEN** the same `Idempotency-Key` and equivalent request body are submitted again — sequentially or concurrently — after a successful first application
- **THEN** the response is `201 Created` identical to the original response, with an additional `Idempotency-Replayed: true` header, and no balance changes a second time

### Requirement: Correlation via X-Correlation-ID

The system SHALL accept an `X-Correlation-ID` request header and echo it on every response, including
error responses; when absent, the system SHALL generate one and include it on the response. (ADR-0056
renames this header from the original `X-Flow-ID`; behavior is unchanged.)

#### Scenario: Client-supplied flow id is echoed

- **WHEN** any request carries `X-Correlation-ID: abc-123`
- **THEN** every response to that request — success or problem — carries `X-Correlation-ID: abc-123`

#### Scenario: Flow id is generated when absent

- **WHEN** a request carries no `X-Correlation-ID`
- **THEN** the response carries a generated `X-Correlation-ID`, and the same value appears in the
  service logs for that request

### Requirement: Error responses use Problem Details

Every error response SHALL use the `application/problem+json` media type with `type`, `title`,
`status`, and `detail` members, plus a stable extension member `code` that clients can assert on.
Internal invariants that indicate a bug SHALL map to `500` without leaking internal detail.

#### Scenario: Error-code to status mapping

- **WHEN** an operation fails with a domain error
- **THEN** the status is `400` for `INVALID_AMOUNT`/`INVALID_ACCOUNT_ID`/`INVALID_TRANSACTION_ID`,
  `404` for `ACCOUNT_NOT_FOUND`, `422` for `INSUFFICIENT_FUNDS`/`SAME_ACCOUNT_TRANSFER`, `409` for
  `TRANSACTION_ID_CONFLICT`, `503` for `CONCURRENCY_CONFLICT`, and `500` for any internal invariant
  failure, with the error's code carried in the `code` member

#### Scenario: Malformed identifiers and bodies

- **WHEN** a path `account-id` is not a valid identifier, or the request body is unreadable JSON
- **THEN** the response is `400 Bad Request` in problem format, and no operation is executed

#### Scenario: Same transaction id reused with different parameters

- **WHEN** a request's `Idempotency-Key` was already applied with different operation parameters than
  this request carries
- **THEN** the response is `409 Conflict` with problem code `TRANSACTION_ID_CONFLICT`, and no balance
  changes as a result of this request

#### Scenario: Temporary capacity exhaustion

- **WHEN** a request cannot acquire the resources it needs (an account lock or a database connection)
  within the system's configured wait budget
- **THEN** the response is `503 Service Unavailable` with problem code `CONCURRENCY_CONFLICT`, a
  `Retry-After` header, and no balance changes as a result of this request

### Requirement: JSON contract conventions

All JSON field names SHALL be camelCase, and all monetary amounts SHALL be integers in minor units as
JSON numbers (single implicit currency). (ADR-0058 amends ADR-0043: JSON naming switches from
snake_case to camelCase; monetary-amount encoding is unchanged.)

#### Scenario: Camel-case wire format

- **WHEN** any response containing an account identifier or transaction identifier is serialized
- **THEN** the fields are named `accountId` and `transactionId`

### Requirement: Machine-readable API documentation
The service SHALL expose a machine-readable OpenAPI 3 description of the full REST contract (paths, request/response schemas, the `Idempotency-Key` header, and typed error responses) at `/v3/api-docs` (JSON) and `/v3/api-docs.yaml`, and SHALL serve an interactive Swagger UI at `/swagger-ui`.

#### Scenario: OpenAPI document is served
- **WHEN** a client requests `GET /v3/api-docs` on a running service
- **THEN** the response is a 200 OpenAPI 3 document covering every endpoint of the REST contract (create account, get balance, credit, debit, transfer)

#### Scenario: Error contract is documented
- **WHEN** a client inspects the OpenAPI document
- **THEN** each command endpoint documents its typed error responses (400 validation, 404 unknown account, 409 transaction-id conflict, 503 concurrency conflict) as RFC 7807 problem details

#### Scenario: Swagger UI is served
- **WHEN** a user opens `/swagger-ui/index.html` in a browser
- **THEN** the interactive UI renders and can execute requests against the running service

#### Scenario: Core semantics unchanged
- **WHEN** any credit, debit, transfer, or balance request is processed
- **THEN** the behavior is byte-for-byte the contract already specified — documentation adds no behavioral change
