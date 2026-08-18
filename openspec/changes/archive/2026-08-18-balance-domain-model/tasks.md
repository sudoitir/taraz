# Tasks: balance-domain-model

Implement in dependency order. Every group ends with its own tests and a green `./mvnw test` (ADR-0022;
no Testcontainers/k6 in this module — it has no infrastructure). TDD per group: write the group's tests
first or alongside each type, never after the whole group.

## 1. ADRs and build setup

- [x] 1.1 Write ADR-0036 (`Money` as `BigDecimal` minor units, single implicit currency) — Persian, `<div dir="rtl">`, Nygard sections, status پذیرفته‌شده
- [x] 1.2 Write ADR-0037 (`Transaction` + `LedgerEntry` double-entry; zero-sum scoped to TRANSFER; clearing account rejected; two-`Account`+one-`Transaction` atomic unit) — same format
- [x] 1.3 Write ADR-0038 (JUG for UUIDv7 behind domain-owned `IdGenerator`; virtual-thread pinning analysis) — same format
- [x] 1.4 Root `pom.xml`: add `jug.version` property + dependencyManagement entry (confirm current 5.x release on Maven Central)
- [x] 1.5 `core/domain/pom.xml`: add JUG dependency, `junit-jupiter` + `assertj-core` (test scope, Boot-managed versions), reword description to "zero framework dependencies"

## 2. `common` foundations

- [x] 2.1 `package-info.java` with `@NullMarked` for `common` (mirror existing wording)
- [x] 2.2 `ErrorCode` enum (the nine catalogued codes), `DomainError` record, sealed `Result<T>` with `map`/`flatMap`/`orElseThrow`/`error()`
- [x] 2.3 `DomainEvent` interface + `AbstractDomainEvent` (builder-built)
- [x] 2.4 `AbstractEntity<ID>` (final getClass-based equals/hashCode) and `AbstractAggregateRoot<ID>` (event recording, `pullDomainEvents` copy-then-clear)
- [x] 2.5 Tests: Result map/flatMap/channels; entity equals reflexive/symmetric/transitive + different-subclass inequality; `pullDomainEvents` returns-then-empties — green `./mvnw test`

## 3. `common` specifications

- [x] 3.1 `Specification<T>` interface + `AbstractSpecification<T>` (check/and/or/not)
- [x] 3.2 Package-private `AndSpecification`, `OrSpecification`, `NotSpecification`
- [x] 3.3 Tests: `and()` left-biased first-violation, `or()`, `not(explicit error)`, `check()` success/failure — green `./mvnw test`

## 4. `common` identity

- [x] 4.1 `IdGenerator` interface (domain-owned)
- [x] 4.2 `UuidV7IdGenerator` (JUG `timeBasedEpochGenerator`)
- [x] 4.3 Tests: version nibble is 7; successive ids lexicographically ordered — green `./mvnw test`

## 5. `money`

- [x] 5.1 `package-info.java` + `Money` record over `BigDecimal` (`of`, `operationAmount`, `plus`, `minus`, `isPositive`, `isGreaterThanOrEqualTo`, `Comparable`; normalize in compact constructor)
- [x] 5.2 Tests: balances accumulate exactly beyond `Long.MAX_VALUE` (no overflow path); minus below zero → `INSUFFICIENT_FUNDS`; `operationAmount(0)` → `INVALID_AMOUNT`; fractional minor units rejected; scale-blind equality (`1.0` == `1.00`); closed under guards — green `./mvnw test`

## 6. `account`

- [x] 6.1 `package-info.java` files, `AccountId` record
- [x] 6.2 `spec/PositiveAmountSpecification`, `spec/SufficientFundsSpecification`
- [x] 6.3 `event/AccountOpened`, `AccountCredited`, `AccountDebited`, `AccountEvents` factory
- [x] 6.4 `Account` aggregate (`open`/`reconstitute`/`credit`/`debit`/`balance()` per design D7)
- [x] 6.5 Tests: debit beyond balance unchanged + `INSUFFICIENT_FUNDS`; debit of exact balance → 0; `amount <= 0` → `INVALID_AMOUNT`; credit accumulates beyond `Long.MAX_VALUE` exactly; `open` emits event, `reconstitute` silent — green `./mvnw test`

## 7. `transaction`

- [x] 7.1 `package-info.java` files, `TransactionId` (client-supplied String, blank → `INVALID_TRANSACTION_ID`), `EntryId`, enums (`TransactionType`, `TransactionStatus`, `EntryDirection`)
- [x] 7.2 `LedgerEntry` child entity (builder-built)
- [x] 7.3 `spec/EntriesMatchTypeSpecification`, `UniformAmountSpecification`, `DistinctTransferAccountsSpecification` (ADR-0028)
- [x] 7.4 `event/TransactionPosted`, `TransactionCompensated`, `TransactionEvents` factory
- [x] 7.5 `Transaction` aggregate: immutable, static `credit`/`debit`/`transfer`/`compensationOf`, `netEffectOn`
- [x] 7.6 Tests: transfer legs net to zero; mismatched leg shapes → `INVALID_ENTRY_SHAPE`/`UNBALANCED_TRANSACTION`; non-uniform amounts rejected; `compensationOf` on all three types reverses every leg + links `compensates` + stays balanced; non-`APPLIED` → `COMPENSATION_TARGET_NOT_APPLIED` — green `./mvnw test`

## 8. `service`

- [x] 8.1 `package-info.java`, `PostingResult` record, `PostingService` (evaluate-all-then-mutate per design D9)
- [x] 8.2 Invariant test suite: successful transfer exact equality both sides; failed transfer leaves both accounts byte-identical; same-account transfer rejected before mutation; no partial state on any failure path — green `./mvnw test`

## 9. Architecture boundaries

- [x] 9.1 New ArchUnit rules in `architecture-tests/.../LayerBoundariesTest.java`: `..core.domain..` may depend only on `java..`, `org.jspecify..`, `com.fasterxml.uuid..`, itself; no `jakarta..`/`lombok..`/`org.mapstruct..`/`org.springframework..`; no `java.util.Date`/`Calendar`/`System.currentTimeMillis`/`Instant.now()`/`UUID.randomUUID()`; `AbstractAggregateRoot` subtypes only in `..core.domain..`; no public setters or public no-arg constructors on `AbstractEntity` subtypes
- [x] 9.2 Green `./mvnw test` + `./mvnw -Pci verify` (Spotless + Error Prone/NullAway)

## 10. Final review gate

- [x] 10.1 Manual review: no `throw` on a predicted failure path; no public setter or no-arg constructor; every aggregate reachable only through a builder; every domain event created only through its factory
- [x] 10.2 All spec scenarios in `specs/balance-domain-model/spec.md` covered by a named test
