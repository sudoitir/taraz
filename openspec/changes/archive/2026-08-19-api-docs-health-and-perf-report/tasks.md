# Tasks: api-docs-health-and-perf-report

## 1. ADRs

- [x] 1.1 Write `docs/adr/0059-springdoc-openapi-docs.md` (Persian, RTL, Nygard template, status پذیرفته‌شده)
- [x] 1.2 Write `docs/adr/0060-actuator-health-only.md` (Persian, RTL, status پذیرفته‌شده)

## 2. OpenAPI / Swagger UI

- [x] 2.1 Add `org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0` to `adapters/driving/rest/pom.xml`
- [x] 2.2 Create `config/OpenApiConfiguration.java` with the `OpenAPI` metadata bean (title, description, version)
- [x] 2.3 Annotate `AccountController`, `AccountOperationsController`, `TransferController` (`@Tag`, `@Operation`, `@Parameter` for `Idempotency-Key`, `@ApiResponse` with `ProblemDetail` for 400/404/409/503)
- [x] 2.4 `./mvnw -pl adapters/driving/rest -am test` green (NullAway, Error Prone, Spotless, slice tests)

## 3. Actuator health-only

- [x] 3.1 Add `spring-boot-starter-actuator` to `container/pom.xml`
- [x] 3.2 Configure `management.endpoints.web.exposure.include: health` and `management.endpoint.health.probes.enabled: true` in `container/src/main/resources/application.yaml`

## 4. Tooling & docs

- [x] 4.1 Add per-OS `docs` recipe to `justfile` (open / xdg-open / start, `${SERVER_PORT:-8080}`)
- [x] 4.2 README (Persian): technology-table rows for springdoc-openapi and Actuator; run section gains `just docs`, `/swagger-ui`, `/v3/api-docs`, `/actuator/health`

## 5. Live verification & perf report

- [x] 5.1 `./mvnw test` — full suite green
- [x] 5.2 `just up && just run` — `/actuator/health` is UP (db, redis, kafka); liveness/readiness probes respond
- [x] 5.3 `curl /v3/api-docs` 200; `/swagger-ui/index.html` 200; `just docs` opens browser
- [x] 5.4 `just k6` — all six scenarios pass; capture p95/p99, throughput, checks
- [x] 5.5 Append Persian «گزارش تست کارایی (k6)» section to README with measured table + environment + date
- [x] 5.6 `just down`
## 6. PostgreSQL observability + benchmark

- [x] 6.1 compose.yaml: postgres `command` preloads pg_stat_statements + auto_explain (50ms threshold); init script creates the extension; `ops/postgres/top-queries.sql` + `just db-stats` recipe
- [x] 6.2 `k6/scenarios/benchmark.js`: constant-arrival-rate sustained load, mixed ops, unique keys, checks on every response; `just benchmark` recipe
- [x] 6.3 Recreate dev DB (`just down-clean && just up`), verify pg_stat_statements works via `just db-stats`

## 7. Ship

- [x] 7.1 `openspec validate api-docs-health-and-perf-report --strict` clean; archive the change
- [x] 7.2 Conventional commit, push to `origin/main`
