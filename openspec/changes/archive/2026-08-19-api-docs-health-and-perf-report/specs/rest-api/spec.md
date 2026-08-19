# Delta: rest-api

## ADDED Requirements

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
