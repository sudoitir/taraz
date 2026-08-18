## Context

See proposal.md — Why. Constraints shaping the approach: ADR-0002 pins Java 21 / Spring Boot 4.1.0 / Maven / virtual threads / groupId `io.github.sudoitir`; ADR-0006 fixes the layer map (`core`, `adapters`, `container`, `architecture-tests`); ADR-0023 requires boundary rules that break `./mvnw test`; ADR-0025 puts dev infra in docker-compose only; ADR-0014 mandates Liquibase and forbids `ddl-auto`; the challenge requires `./mvnw test` as the single standard command and a Persian README.

## Goals / Non-Goals

**Goals:**
- Compile-time layer isolation: wrong dependencies cannot even compile, not merely fail tests
- A green `./mvnw -B verify` from a clean checkout with no Docker (keeps CI fast and honest)
- Formatting invisible to developers (applied during compile), enforced in CI

**Non-Goals:**
- Any business code, endpoints, changesets, or Kafka client (see proposal Non-goals)
- Maven `flatten` plugin, release/artifact publishing setup — YAGNI for a local challenge repo

## Decisions

### D1: Multi-module Maven over single-module packages
Top-level modules `core`, `adapters`, `container`, `architecture-tests`, with the ADR-0006 map mirrored as a **hierarchy** (ADR-0033): `core` aggregates `domain` + `application` (pom), and `application` aggregates `port` (packages `inbound`/`outbound`), `service` (CQRS write), `query` (CQRS read); `adapters` aggregates `driving` (pom → `rest`) and `driven` (pom → `persistence`, `messaging`). Leaf artifactIds are plain directory names (`domain`, `port`, `service`, `query`, `rest`, `persistence`, `messaging`, `container`). Multi-module makes the dependency direction (`container` → adapters → application → `domain`) a compile-time guarantee, with ArchUnit (ADR-0023) as the second, finer-grained layer inside modules — including `driving` ✗ outbound ports, `driving` ✗ `service`, `driving` ✗ `domain`, `driven` ✗ `driving`. Alternative (single module, package separation, ArchUnit-only) was rejected: package rules are easier to accidentally break than module boundaries, and the challenge grades design rigor.

### D2: `core` has zero dependencies
Not even Spring or Jakarta annotations. Pure domain/application layer per ADR-0005/0006. Cost: some later mapping of framework concerns into adapters — accepted, that is the point of the architecture.

### D3: Spotless + palantir-java-format, `apply` bound to compile, `check` in CI
Spotless maven plugin 3.10.0, palantir-java-format 2.97.0, `removeUnusedImports`, `importOrder` following Spring Framework conventions (`java` → `javax`/`jakarta` → `org` → `com` → others → static last, blank-line separated). Rationale: palantir is the modern google-java-format with Java 21 support; binding `apply` to the `compile` phase means code is always formatted before compilation, so `check` in CI is a verification, not a developer chore. This decision is recorded in ADR-0029.

### D4: spring-data-redis (Lettuce) as the Valkey client
Valkey speaks the Redis protocol (ADR-0020); no `spring-data-valkey` artifact exists (verified on Maven Central 2026-08-18). Using the Boot-managed `spring-boot-starter-data-redis` keeps versions aligned with Boot 4.1.0.

### D5: Spring Boot 4.1 canonical starter names
`spring-boot-starter-webmvc` (not deprecated `-web`), `spring-boot-starter-validation`, `spring-boot-starter-data-jpa`, `spring-boot-starter-test` — verified against Boot 4.1.0 docs via context7.

### D6: Testcontainers present but unused at this stage
`org.testcontainers:testcontainers-junit-jupiter` + `testcontainers-postgresql` (2.0.5, Boot-managed) land in `container` test scope so the next change (persistence) writes integration tests immediately. The ArchUnit smoke test is plain JUnit — no Spring context, no Docker — so `./mvnw test` stays docker-free.

### D7: Infra credentials via `.env` + `.env.example`
`compose.yaml` interpolates `${POSTGRES_PASSWORD}` etc. from `.env` (gitignored); `.env.example` is committed with placeholder values. Spring Boot's docker-compose support reads the same compose file for dev-time connection auto-configuration.

### D8: Architecture-tests as a separate module
Depends on all other modules in test scope so rules can import every layer's classes. Keeps ADR-0023's dedicated home for rules and prevents test rules from living in (and coupling) the production modules.

## Risks / Trade-offs

- Spring Boot 4.1.0 is very new; managed dependency versions may shift → pinned exact versions verified on Maven Central; CI catches drift
- Spotless `apply` mutating sources during build can surprise developers → documented in README; it is idempotent and git makes changes visible
- Kafka image (apache/kafka:4.3) in compose but no client yet → acceptable: ADR-0025 provisions infra independently of app dependencies
- Multi-module adds pom ceremony (~4 small files) → one-time cost for permanent compile-time boundary enforcement
