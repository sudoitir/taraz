# Delta: project-scaffolding

## ADDED Requirements

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
