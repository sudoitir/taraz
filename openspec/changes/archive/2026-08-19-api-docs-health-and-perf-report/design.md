# Design: api-docs-health-and-perf-report

## Context

See proposal.md — Why. Current state: REST adapter (`adapters/driving/rest/`) serves the full contract but has no API documentation; the container module has no actuator; the justfile has no browser-opening recipe; k6 results are not persisted anywhere. Constraints: ADR rules (new technology → ADR), docs-fa rules (Persian README/ADRs), challenge minimalism (justify every added technology).

## Goals / Non-Goals

**Goals:**
- Live OpenAPI 3 + Swagger UI generated from the code (never a hand-maintained spec file that can drift).
- Health-only actuator with k8s-style probes.
- One-command access to docs (`just docs`) on macOS/Linux/Windows.
- README carries measured k6 results, not claims.

**Non-Goals:** metrics/tracing endpoints, a new k6 load scenario, security hardening of actuator beyond "health-only exposure" (single-node dev/review deployment).

## Decisions

1. **springdoc-openapi 3.1.0 (`springdoc-openapi-starter-webmvc-ui`) in the REST driving adapter** — the 3.x line is built against Spring Boot 4.x (verified against project docs and Maven Central; fallback 3.0.3 if a runtime incompatibility appears). Placing it in the rest adapter keeps the API and its documentation in one module; the container inherits it transitively. Alternatives considered: (a) container module — works, but docs would live away from the API they describe; (b) hand-written `openapi.yaml` — rejected, drifts from code; (c) Spring REST Docs — test-driven but heavier and not interactive.
2. **One `OpenApiConfiguration` bean + concise annotations, no parallel spec artifacts** — springdoc infers schemas from controllers/DTOs; we add only what inference cannot know: API title/description/version, per-operation summaries, the required `Idempotency-Key` header parameter, and `@ApiResponse` entries referencing `ProblemDetail` for the typed errors already produced by `ProblemAdvice`/`ProblemFactory`. Alternative (full annotation carpet) rejected as bloat.
3. **Actuator in the container module, health-only** — ops concern, belongs at app assembly. Config: `management.endpoints.web.exposure.include: health` and `management.endpoint.health.probes.enabled: true`. db/redis/kafka health indicators auto-configure from the existing starters. Alternative (expose `info,metrics`) rejected — nothing consumes metrics today.
4. **Per-OS browser open inside one bash recipe, not just's `os()`** — a `case "$(uname -s)"` in a `#!/usr/bin/env bash` recipe covers macOS (`open`), Linux (`xdg-open`), Windows (`cmd.exe /c start`) in three lines with zero just-specific magic; URL uses `${SERVER_PORT:-8080}` (`.env` already loaded via `set dotenv-load`).
5. **k6 performance report = measured facts only** — every number in the README report comes from a live run on a stated machine/toolchain; nothing estimated. Includes the six correctness scenarios plus a dedicated benchmark (decision 7) and pg_stat_statements evidence (decision 8).
6. **Concurrency / idempotency / transactions** — this change adds read-only documentation and health surfaces; it touches no command path, no lock, no transaction boundary (ADR-0018/0026/0042 unaffected). Verified by the existing IT suite staying green.
7. **Benchmark scenario: constant-arrival-rate, mixed workload** — `k6/scenarios/benchmark.js`: ~200 pre-funded accounts, constant arrival rate (hundreds of rps, ~60s), weighted mix (credit/debit/transfer/balance-read), unique key per operation, every response checked. Correctness-under-load is already proven by the six scenarios; the benchmark measures throughput/latency without distorting the measurement with correctness bookkeeping beyond pass/fail checks. `just benchmark` runs it separately from `just k6` (correctness suite).
8. **pg_stat_statements + auto_explain in dev compose only** — preload via postgres `command` flags in `compose.yaml`, `CREATE EXTENSION` via a `docker-entrypoint-initdb.d` script, top-queries SQL + `just db-stats` recipe. Dev-only: Testcontainers ITs run stock postgres (no preload), production config is out of scope. auto_explain threshold 50ms — the app's query profile is PK lookups and short row-lock writes; anything slower deserves a logged plan.
9. **Bug fixes folded in (found by live verification this change introduced)**: (a) `ProblemAdvice` swallowed Spring's status-carrying exceptions into opaque 500s — any unknown path returned 500 instead of 404; fixed at the root (`NoResourceFoundException` + `ErrorResponseException` keep their status), proven by a new slice test. (b) transfer-atomicity derived direction from global `__VU` parity — broken by the monitor VU's global id; fixed with scenario-local `iterationInTest` parity. Ledger forensics during debugging double-verified exactly-once and balance==ledger consistency on real data.

## Risks / Trade-offs

- springdoc 3.1.0 incompatibility with Boot 4.1.0 → fall back to 3.0.3; both are on the Boot-4 line.
- Annotations add compile-time coupling of controllers to springdoc → confined to the driving adapter; core modules stay dependency-free (ArchUnit layer tests must stay green).
- Actuator widens the HTTP surface → only `health` is exposed; every other actuator endpoint 404s (spec scenario).
- README perf numbers age → report states environment and date; it is a snapshot, honestly framed.
