# project-scaffolding Specification

## Purpose
Provides the buildable, verifiable project skeleton for Taraz: a Maven multi-module structure with enforced layer boundaries, a pinned toolchain, compile-time code formatting, one-command build/test, and docker-compose development infrastructure.
## Requirements
### Requirement: One-command build and test

The project SHALL build and run all tests with the single standard command `./mvnw test` from the
repository root, without requiring Docker or any manually installed service. Tests that need real
infrastructure SHALL run within this same command when Docker is available, and SHALL skip cleanly
— never fail — when it is not.

#### Scenario: Clean checkout builds and tests

- **WHEN** a developer clones the repository on a machine with JDK 21 and runs `./mvnw test`
- **THEN** all modules compile and all tests pass without starting any external service

#### Scenario: Task runner wraps the standard command

- **WHEN** a developer runs `just test`
- **THEN** it executes `./mvnw test` and reports the same result

#### Scenario: Infrastructure-backed tests skip cleanly without Docker

- **WHEN** `./mvnw test` runs on a machine without Docker available
- **THEN** tests requiring real PostgreSQL, Valkey, or Kafka report as skipped, the build still
  succeeds, and no other test is affected

### Requirement: Layered module structure

The project SHALL consist of a nested Maven module hierarchy under `core` (`domain`, and
`application` with `port`, `service`, `query`), `adapters` (`driving` with `rest`, and `driven` with
`persistence` and `messaging`), `container`, and `architecture-tests` — where `core` has no framework
dependencies, `adapters` depends on `core`, and `container` is the runnable Spring Boot application.

#### Scenario: Core stays framework-free

- **WHEN** any dependency is added to the `core` module
- **THEN** it is not a Spring, Jakarta EE web, or persistence framework dependency

#### Scenario: Application boots

- **WHEN** the development infrastructure is running and the application is started
- **THEN** the Spring Boot context in `container` starts successfully with virtual threads enabled

### Requirement: Architecture boundaries break the build
Architecture rules SHALL be verified by automated tests that run as part of `./mvnw test`, so a boundary violation fails the build.

#### Scenario: Layer violation detected
- **WHEN** code in `core` references a class in `adapters` or a Spring framework package
- **THEN** `./mvnw test` fails with an architecture-rule violation

### Requirement: Consistent formatting enforced at build time
Java sources SHALL be formatted automatically during the build, and the CI pipeline SHALL fail on unformatted code.

#### Scenario: Format applied on compile
- **WHEN** a developer compiles the project with unformatted Java sources
- **THEN** the sources are reformatted to the project style during the build

#### Scenario: CI rejects unformatted code
- **WHEN** a commit contains Java code violating the project style
- **THEN** the CI build fails on the format check

### Requirement: Development infrastructure via compose
PostgreSQL, Valkey, and Kafka SHALL be provisionable for local development with a single `docker compose up`, using credentials from a local environment file that is never committed.

#### Scenario: Infra starts from compose
- **WHEN** a developer copies `.env.example` to `.env` and runs `docker compose up -d`
- **THEN** PostgreSQL, Valkey, and Kafka containers become healthy

#### Scenario: Secrets stay out of git
- **WHEN** a developer commits changes
- **THEN** `.env` is ignored by git while `.env.example` is tracked


### Requirement: Application health endpoint
The service SHALL expose a health endpoint at `/actuator/health` aggregating the components it depends on (database, cache, broker), plus liveness and readiness probes at `/actuator/health/liveness` and `/actuator/health/readiness`. Only the health endpoint group SHALL be exposed over HTTP.

#### Scenario: All components healthy
- **WHEN** PostgreSQL, Valkey, and Kafka are reachable and a client requests `GET /actuator/health`
- **THEN** the response is 200 with overall status `UP` and per-component status entries

#### Scenario: Dependency down
- **WHEN** a required dependency (e.g. the database) is unreachable and a client requests `GET /actuator/health`
- **THEN** the response is 503 with overall status `DOWN` identifying the failed component

#### Scenario: Only health is exposed
- **WHEN** a client requests any other actuator endpoint (e.g. `/actuator/metrics`, `/actuator/env`)
- **THEN** the endpoint is not available (404)

### Requirement: Cross-platform docs launcher task
The justfile SHALL provide a `docs` recipe that opens the Swagger UI of the locally running service in the user's default browser on macOS, Linux, and Windows.

#### Scenario: Open docs on any supported OS
- **WHEN** the user runs `just docs` on macOS, Linux, or Windows with the app running
- **THEN** the default browser opens the Swagger UI URL (honoring `SERVER_PORT` from `.env`, default 8080)

### Requirement: PostgreSQL performance observability in dev compose
The dev-compose PostgreSQL SHALL preload `pg_stat_statements` and `auto_explain` (with a slow-statement threshold), the extension SHALL be created on fresh volumes, and the justfile SHALL provide a `db-stats` recipe that prints the top statements by total time.

#### Scenario: Statement statistics are collected
- **WHEN** the dev database has served traffic and the user runs `just db-stats`
- **THEN** per-statement statistics (calls, mean and total time) for the service's queries are printed

#### Scenario: Slow plans are logged
- **WHEN** a statement exceeds the auto_explain duration threshold
- **THEN** its execution plan appears in the PostgreSQL container logs
