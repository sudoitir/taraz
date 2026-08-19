--liquibase formatted sql

--changeset taraz:0003-ledger-entry-create
--comment ADR-0015: flat entity, plain FK columns — no relation graph, no hidden fetch strategy.
CREATE TABLE ledger_entry (
    created_at         timestamptz NOT NULL,
    id                 uuid        NOT NULL,
    transaction_id     uuid        NOT NULL,
    account_id         uuid        NOT NULL,
    direction          varchar(8)  NOT NULL,
    amount_minor_units numeric     NOT NULL,
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entry_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transaction (id),
    CONSTRAINT fk_ledger_entry_account FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_entry_amount_positive CHECK (amount_minor_units > 0),
    CONSTRAINT ck_ledger_entry_amount_whole CHECK (amount_minor_units = trunc(amount_minor_units))
);
--rollback DROP TABLE ledger_entry;

--changeset taraz:0003-ledger-entry-indexes
--comment ADR-0019: exactly two indexes, each serving a query that exists.
--  ix_ledger_entry_transaction serves the FK integrity check (Postgres does not auto-index FKs)
--  and "legs of transaction X" (audit). ix_ledger_entry_account_created serves
--  "statement for account X, newest first" — the only planned audit query shape.
CREATE INDEX ix_ledger_entry_transaction ON ledger_entry (transaction_id);
CREATE INDEX ix_ledger_entry_account_created ON ledger_entry (account_id, created_at DESC);
--rollback DROP INDEX ix_ledger_entry_account_created; DROP INDEX ix_ledger_entry_transaction;
