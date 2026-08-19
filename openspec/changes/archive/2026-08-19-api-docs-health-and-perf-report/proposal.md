# Proposal: api-docs-health-and-perf-report

## Why

The challenge is feature-complete, but the API contract is only discoverable by reading code, the app exposes no health signal of its own, and the k6 results exist only in terminal scrollback. For a reviewer (and for production operability) the service should document its own API, report its own health, and record measured performance in the README.

## What Changes

- Add **springdoc-openapi** (`springdoc-openapi-starter-webmvc-ui` 3.1.0, Spring Boot 4.x line) to the REST driving adapter: `/v3/api-docs` + Swagger UI at `/swagger-ui`, an `OpenAPI` metadata bean, and concise `@Tag`/`@Operation`/`@Parameter`/`@ApiResponse` annotations on the three controllers — governed by new **ADR-0059**.
- Add **Spring Boot Actuator, health-only** to the container module: expose only `health`, enable liveness/readiness probes — governed by new **ADR-0060**.
- Add a per-OS `just docs` recipe that opens Swagger UI in the default browser (`open` / `xdg-open` / `start`).
- Enable **PostgreSQL performance observability in the dev compose**: `pg_stat_statements` + `auto_explain` preloaded, plus a `just db-stats` recipe to inspect the top queries.
- Add a dedicated **k6 benchmark scenario** (`just benchmark`): sustained constant-arrival-rate load with a realistic operation mix, reporting throughput and latency percentiles.
- README (Persian): technology-table rows for springdoc + Actuator, run/docs URLs, and a final **performance report** section containing only measured facts from a live run (all six correctness scenarios + the benchmark + pg_stat_statements evidence), with the exact test environment.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `rest-api`: new requirement — the service exposes a machine-readable OpenAPI 3 description of the REST contract and an interactive Swagger UI.
- `project-scaffolding`: new requirements — the app exposes a health/readiness endpoint; the justfile provides a per-OS recipe to open the API docs; the dev PostgreSQL carries pg_stat_statements + auto_explain with a stats recipe.
- `k6-tests`: new requirement — a benchmark scenario applies sustained load and reports measured throughput/latency.

## Impact

- `adapters/driving/rest/pom.xml` — one dependency; new `config/OpenApiConfiguration.java`; annotations on `AccountController`, `AccountOperationsController`, `TransferController`; `ProblemAdvice` now preserves Spring's status-carrying exceptions (404/405/415 were misreported as 500 — found via live verification).
- `container/pom.xml` — `spring-boot-starter-actuator`; `container/src/main/resources/application.yaml` — `management.*` health config; new `config/KafkaHealthConfiguration.java` (Boot 4 dropped the auto-configured Kafka indicator).
- `compose.yaml` — PostgreSQL preloads pg_stat_statements + auto_explain; new `ops/postgres/` init + stats SQL.
- `k6/scenarios/benchmark.js` — new; `k6/scenarios/transfer-atomicity.js` — direction split fixed (global `__VU` parity was broken by the monitor VU; now scenario-local `iterationInTest` parity).
- `justfile` — new `docs`, `db-stats`, `benchmark` recipes.
- `README.md` — tech table, run section, performance report (measured live).
- `docs/adr/0059-springdoc-openapi-docs.md`, `docs/adr/0060-actuator-health-only.md` — new ADRs.
- No behavioral change to credit/debit/transfer/balance semantics; no breaking change.

## Non-goals

- Metrics/tracing endpoints (Micrometer, Prometheus) — health only; tracing stays on the README remaining list.
- Compensate handlers (ADR-0035) — unchanged, still documented as not implemented.
- Performance *tuning* — the benchmark measures and reports; changing pool/DB parameters is out of scope unless a measurement shows a defect.
