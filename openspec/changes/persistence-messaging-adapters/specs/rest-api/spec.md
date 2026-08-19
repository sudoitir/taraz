## MODIFIED Requirements

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
