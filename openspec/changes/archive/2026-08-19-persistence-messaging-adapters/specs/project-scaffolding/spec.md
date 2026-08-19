## MODIFIED Requirements

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
