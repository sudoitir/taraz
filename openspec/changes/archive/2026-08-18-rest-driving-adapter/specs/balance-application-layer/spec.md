## ADDED Requirements

### Requirement: Account opening use case

The system SHALL provide a use case that opens a new account with a server-generated identifier and zero balance, records the opening as a domain event appended to the outbox, and persists the account atomically within the unit of work. Opening an account is not a financial transaction: it SHALL NOT consume a transaction identifier, SHALL NOT pass through the idempotency gate, and SHALL NOT be recorded in the processed-transaction store.

#### Scenario: Open an account

- **WHEN** the open-account use case is invoked
- **THEN** a new account exists with a unique server-generated identifier and balance zero, its `AccountOpened` event is appended to the outbox, and the caller receives the account identifier and balance

#### Scenario: Repeated invocations create distinct accounts

- **WHEN** the use case is invoked twice
- **THEN** two distinct accounts with different identifiers exist, each with balance zero
