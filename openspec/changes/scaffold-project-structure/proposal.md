## Why

The repository contains only architecture decisions (ADRs 0001–0028), rules, and an empty OpenSpec tree — no buildable code exists. Every subsequent feature (balance operations, idempotency, transfers, outbox) needs a compliant project skeleton to land in. This change bootstraps the Maven multi-module structure exactly as decided in the ADRs, so all later work starts from a green, enforceable baseline.

## What Changes

- Create Maven multi-module build: parent `taraz` pom (Spring Boot 4.1.0 parent, Java 21, `io.github.sudoitir`) with modules **`core`**, **`adapters`**, **`container`**, **`architecture-tests`** (ADR-0024, ADR-0006, ADR-0002)
- `core`: pure Java (zero framework deps) — domain + application layer package skeleton
- `adapters`: Spring dependencies for driving (webmvc, validation) and driven (data-jpa, data-redis/Lettuce→Valkey) adapters
- `container`: Spring Boot application entry point, Liquibase master changelog, PostgreSQL driver, `application.yaml` (virtual threads enabled, `ddl-auto=none`), spring-boot-docker-compose
- `architecture-tests`: ArchUnit 1.5.0 smoke rules enforcing layer boundaries, breaking `./mvnw test` on violation (ADR-0023)
- Spotless (palantir-java-format 2.97.0, Spring-Framework import order, removeUnusedImports) with `apply` bound to compile phase — recorded in new ADR-0029
- `compose.yaml` dev infra: postgres:18, valkey/valkey:9, apache/kafka:4.3 (ADR-0025)
- `justfile` as single task entry point (ADR-0004): test/build/format/run/up/down
- Maven wrapper; `.env.example` + `.env` for infra credentials; `.gitignore` updated (`.env` excluded)
- Persian `README.md` skeleton with the 8 challenge-mandated sections, honestly marking scaffolded vs pending
- Activates the existing guarded CI workflow (`hashFiles('pom.xml')`)

## Capabilities

### New Capabilities

- `project-scaffolding`: The buildable, verifiable project skeleton — module structure, build toolchain (Maven wrapper, formatter, enforcer), dev infrastructure provisioning (docker-compose), task runner, and architecture-boundary enforcement. Covers the observable guarantees: one-command build/test, formatting applied at compile time, layer rules breaking the build, and infra available via compose.

### Modified Capabilities

(none — no specs exist yet)

## Non-goals

- No business logic: no domain model, balance operations, idempotency, or transfer code (separate changes)
- No Kafka client dependency (`spring-kafka`) — compose provisions the broker; the client arrives with the outbox publisher (ADR-0010, ADR-0027)
- No UUID-v7 library (arrives with the domain model, ADR-0016)
- No REST endpoints, no Liquibase changesets (only the empty master changelog), no actuator
- No multi-environment configuration, no production deployment setup

## Impact

- **Code**: new `pom.xml` × 5, module source trees, `TarazApplication`, `application.yaml`, Liquibase master changelog, ArchUnit test, `compose.yaml`, `justfile`, `.env.example`, `.env`, `README.md`, `docs/adr/0029`
- **Dependencies**: Spring Boot 4.1.0 BOM, Liquibase, PostgreSQL JDBC, spring-data-redis (Lettuce), ArchUnit 1.5.0, Spotless 3.10.0 + palantir-java-format 2.97.0, Testcontainers 2.0.5 (Boot-managed)
- **Systems**: local Docker (postgres:18, valkey:9, kafka:4.3); GitHub Actions CI becomes active on push
- **ADRs**: follows 0002, 0004, 0006, 0013, 0014, 0020, 0023, 0024, 0025, 0027; introduces 0029 (formatter)
- **Rules**: challenge-delivery (`./mvnw test`, README sections, justification of optional infra), challenge-testing (test runs via one standard command)
