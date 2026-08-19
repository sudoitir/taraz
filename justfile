# ADR-0004: single entry point for everyday tasks. `just test` wraps `./mvnw test`.

set dotenv-load := true

# Run all tests (the challenge's standard command)
test:
    ./mvnw test

# Full build with verification (compile, format-apply, tests)
build:
    ./mvnw -B verify

# Apply Spotless formatting (palantir-java-format + Spring import order)
format:
    ./mvnw spotless:apply

# Run the application (boots compose services via spring-boot-docker-compose)
run:
    ./mvnw -q -DskipTests install
    ./mvnw -pl container spring-boot:run

# Start dev infrastructure (PostgreSQL, Valkey, Kafka)
up:
    docker compose up -d

# Run all k6 challenge scenarios (app must be running)
k6:
    #!/usr/bin/env bash
    set -e
    for s in smoke validation idempotency concurrency-single-account concurrency-multi-account transfer-atomicity; do
        echo "=== $s ==="
        k6 run k6/scenarios/$s.js
    done

# Run the sustained-load benchmark (app must be running; RATE/DURATION overridable)
benchmark:
    k6 run k6/scenarios/benchmark.js

# Top SQL statements by total time from pg_stat_statements (dev DB must be running)
db-stats:
    docker exec -i taraz-postgres-1 psql -U taraz -d taraz < ops/postgres/top-queries.sql

# Open Swagger UI in the default browser, per-OS (app must be running)
docs:
    #!/usr/bin/env bash
    url="http://localhost:${SERVER_PORT:-8080}/swagger-ui/index.html"
    case "$(uname -s)" in
        Darwin) open "$url" ;;
        Linux)  xdg-open "$url" ;;
        *)      cmd.exe /c start "$url" ;;
    esac

# Stop dev infrastructure
down:
    docker compose down

# Stop infra and delete volumes
down-clean:
    docker compose down -v
