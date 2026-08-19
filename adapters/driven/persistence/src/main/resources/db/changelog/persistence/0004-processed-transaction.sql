--liquibase formatted sql

--changeset taraz:0004-processed-transaction-create
--comment ADR-0021/0041/0047: the authoritative idempotency record. PK on the natural key IS the
--  unique shield ADR-0021 describes and ADR-0048's failure translator matches on by name — do not
--  rename pk_processed_transaction without updating PersistenceFailureTranslator and SchemaIT.
--  outcome is jsonb: an opaque CommandOutcome snapshot (amounts as decimal strings, never JSON
--  numbers — Money is exact BigDecimal and a JSON number can round-trip through a double).
CREATE TABLE processed_transaction (
    recorded_at             timestamptz NOT NULL,
    ledger_transaction_id   uuid        NOT NULL,
    transaction_id          varchar(64) NOT NULL,
    outcome                 jsonb       NOT NULL,
    CONSTRAINT pk_processed_transaction PRIMARY KEY (transaction_id),
    CONSTRAINT fk_processed_transaction_ledger FOREIGN KEY (ledger_transaction_id)
        REFERENCES ledger_transaction (id)
);
--rollback DROP TABLE processed_transaction;
