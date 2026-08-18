## MODIFIED Requirements

### Requirement: Same-account transfer rejected

A transfer whose source and destination are the same account SHALL be rejected before any state is evaluated or mutated (ADR-0028). This check SHALL be expressible as a specification over the pair of account identifiers alone — independent of amount, transaction id, or Transaction construction — so a caller can reject a same-account transfer before opening any lock or transaction.

#### Scenario: Transfer from an account to itself

- **WHEN** `transfer(A, A, 100, txId)` is requested
- **THEN** the operation fails with error code `SAME_ACCOUNT_TRANSFER` and A's balance is unchanged

#### Scenario: Same-account check available standalone

- **WHEN** the source and destination account identifiers are checked using only the two identifiers, before any Transaction is built, any lock is acquired, or any amount is validated
- **THEN** identical identifiers fail with error code `SAME_ACCOUNT_TRANSFER`

## ADDED Requirements

### Requirement: Account identifier validation

`AccountId` SHALL provide a validating factory for client-supplied string input, returning a typed failure for blank or absent input, mirroring the existing validating route on `TransactionId`.

#### Scenario: Blank or missing identifier rejected

- **WHEN** the validating `AccountId` factory is given a blank or null string
- **THEN** it fails with error code `INVALID_ACCOUNT_ID` and no `AccountId` instance is created

#### Scenario: Well-formed identifier accepted

- **WHEN** the validating `AccountId` factory is given a well-formed, non-blank identifier string
- **THEN** it succeeds and returns the corresponding `AccountId`

### Requirement: Account identifier has one canonical total ordering

`AccountId` SHALL define a total ordering that agrees with PostgreSQL's native ordering of the same identifier values, so that a lock-acquisition order computed in application code and any ordering computed by a query against the persisted identifiers always agree.

#### Scenario: Ordering matches unsigned byte comparison

- **WHEN** two `AccountId` values are compared, including a pair whose underlying identifiers differ only in their high bit
- **THEN** the result matches unsigned lexicographic comparison of the identifiers' bytes, not signed comparison of their constituent halves

#### Scenario: Ordering is consistent and total

- **WHEN** any two distinct `AccountId` values are compared
- **THEN** the comparison is antisymmetric and transitive, and comparing a value to itself yields equality

### Requirement: Error catalog covers identifier resolution failures

The domain's catalog of predicted failures SHALL include a code for a referenced account that does not exist and a code for a malformed account identifier, so that callers resolving accounts before invoking domain operations report failures through the same `Result`/`ErrorCode` channel as domain-internal rules, never an ad hoc error type.

#### Scenario: Unknown-account code available

- **WHEN** a caller needs to signal that an `accountId` does not correspond to any known account
- **THEN** it reports `ErrorCode.ACCOUNT_NOT_FOUND` from the shared catalog

#### Scenario: Malformed-identifier code available

- **WHEN** the validating `AccountId` factory rejects its input
- **THEN** it reports `ErrorCode.INVALID_ACCOUNT_ID` from the shared catalog
