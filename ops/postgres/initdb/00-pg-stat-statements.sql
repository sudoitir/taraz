-- Runs once on a fresh postgres-data volume (docker-entrypoint-initdb.d).
-- The library itself is preloaded via compose.yaml's command flags.
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;
