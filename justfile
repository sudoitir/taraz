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

# Stop dev infrastructure
down:
    docker compose down

# Stop infra and delete volumes
down-clean:
    docker compose down -v
