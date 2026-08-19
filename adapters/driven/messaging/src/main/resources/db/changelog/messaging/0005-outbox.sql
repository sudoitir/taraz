--liquibase formatted sql

--changeset taraz:0005-outbox-create
--comment ADR-0010/0027/0049/0050: outbox row carries the FINAL wire bytes (the publisher is a dumb
--  pipe). No FK to ledger_transaction — the appender's JDBC insert runs inside the same DB
--  transaction as the JPA writes (ADR-0049), which flush at commit, after this insert; a FK
--  referencing a not-yet-flushed row would fail.
CREATE TABLE outbox (
    occurred_at     timestamptz NOT NULL,
    created_at      timestamptz NOT NULL,
    next_attempt_at timestamptz NOT NULL,
    published_at    timestamptz,
    attempts        integer     NOT NULL DEFAULT 0,
    id              uuid        NOT NULL,
    aggregate_type  varchar(16) NOT NULL,
    event_type      varchar(48) NOT NULL,
    event_version   varchar(8)  NOT NULL,
    topic           varchar(96) NOT NULL,
    partition_key   varchar(64) NOT NULL,
    aggregate_id    varchar(64) NOT NULL,
    transaction_id  varchar(64),
    correlation_id  varchar(64),
    payload         jsonb       NOT NULL,
    CONSTRAINT pk_outbox PRIMARY KEY (id)
);
--rollback DROP TABLE outbox;

--changeset taraz:0005-outbox-indexes
--comment ADR-0019: exactly the poller's predicate, and exactly the cleaner's predicate. Nothing else.
--  Poller:  WHERE published_at IS NULL AND next_attempt_at <= now() ORDER BY id LIMIT n FOR UPDATE SKIP LOCKED
--  Cleaner: WHERE published_at < now() - retention
CREATE INDEX ix_outbox_pending ON outbox (next_attempt_at, id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_published ON outbox (published_at) WHERE published_at IS NOT NULL;
--rollback DROP INDEX ix_outbox_published; DROP INDEX ix_outbox_pending;

--changeset taraz:0005-outbox-storage
--comment ADR-0019/0055: the hot churn table — every row is INSERT -> UPDATE -> DELETE within days.
--  scale_factor 0 + absolute threshold: vacuum must fire on row count, not on a fraction of a table
--  whose steady-state size is small but whose churn is not.
ALTER TABLE outbox SET (
    fillfactor = 70,
    autovacuum_vacuum_scale_factor = 0.0,
    autovacuum_vacuum_threshold = 1000,
    autovacuum_analyze_scale_factor = 0.0,
    autovacuum_analyze_threshold = 1000,
    autovacuum_vacuum_cost_delay = 0
);
--rollback ALTER TABLE outbox RESET (fillfactor, autovacuum_vacuum_scale_factor, autovacuum_vacuum_threshold, autovacuum_analyze_scale_factor, autovacuum_analyze_threshold, autovacuum_vacuum_cost_delay);
