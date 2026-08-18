## Purpose

Provides the buildable, verifiable project skeleton for Taraz: a Maven multi-module structure with enforced layer boundaries, a pinned toolchain, compile-time code formatting, one-command build/test, and docker-compose development infrastructure.

## ADDED Requirements

### Requirement: One-command build and test
The project SHALL build and run all tests with the single standard command `./mvnw test` from the repository root, without requiring Docker or any manually installed service.

#### Scenario: Clean checkout builds and tests
- **WHEN** a developer clones the repository on a machine with JDK 21 and runs `./mvnw test`
- **THEN** all modules compile and all tests pass without starting any external service

#### Scenario: Task runner wraps the standard command
- **WHEN** a developer runs `just test`
- **THEN** it executes `./mvnw test` and reports the same result

### Requirement: Layered module structure
The project SHALL consist of four Maven modules — `core`, `adapters`, `container`, `architecture-tests` — where `core` has no framework dependencies, `adapters` depends on `core`, and `container` is the runnable Spring Boot application.

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
