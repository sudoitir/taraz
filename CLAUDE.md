# Taraz — Concurrent Balance Service

Senior Java coding challenge (`docs/Coding_Challenge_V2_English.md`): a service that manages account balances **correctly, consistently, and reliably under high concurrency**.

## Stack

- Java 21, Spring Boot **4.1.0**, Maven, virtual threads — groupId `io.github.sudoitir`
- PostgreSQL (system of record), Valkey (cache + idempotency), Liquibase (XML + SQL changesets)
- Architecture: DDD + Explicit Architecture + CQRS + event-driven (outbox)
- Build & test: `./mvnw test` (via `just` tasks once scaffolded)
- **All architecture decisions are recorded in `docs/adr/0001–0023` — follow them; conflicts must be flagged, not improvised.**

## Priorities (from the challenge)

Quality over features: **Correctness → Concurrency → Consistency → Idempotency → Design → Testability**. A simple, well-designed solution beats a feature-rich one. Do not add infrastructure (DB, Redis, Kafka, Docker) unless it solves a real problem — and justify every addition with its trade-offs.

## Core invariants (must always hold)

- No negative balance, no lost updates, no partial operations.
- A `transactionId` affects the balance **exactly once**, even under concurrent retries.
- Transfers are **atomic** on both accounts; amounts deducted and credited are exactly equal.
- Independent accounts must not block each other without a valid reason.

## Workflow rules

- **Architecture decisions in `docs/adr/` are binding** — see @.claude/rules/adr.md
- **OpenSpec (SDD) is mandatory**: every feature/implementation MUST go through `/opsx:propose` → `/opsx:apply` → `/opsx:archive` (`/opsx:explore` for pre-proposal thinking). No code without an approved proposal — see @.claude/rules/openspec.md
- **Library/framework docs via context7** — see @.claude/rules/context7.md
- **README.md and ADRs in Persian** — see @.claude/rules/docs-fa.md

## Challenge rules (derived from the challenge doc — follow all of them)

- @.claude/rules/challenge-concurrency.md
- @.claude/rules/challenge-idempotency.md
- @.claude/rules/challenge-consistency.md
- @.claude/rules/challenge-transfer.md
- @.claude/rules/challenge-testing.md
- @.claude/rules/challenge-delivery.md
