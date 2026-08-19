--liquibase formatted sql

--changeset taraz:0001-account-create
--comment ADR-0013/0016/0019/0044: UUIDv7 PK; balance is unbounded numeric (ADR-0044 narrows ADR-0019).
--  Column order is fixed-width first (timestamptz 8-byte, uuid fixed 16-byte), varlena (numeric) last,
--  to avoid alignment padding. No @Version column — ADR-0026 supersedes ADR-0017's optimistic locking.
CREATE TABLE account (
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    id                  uuid        NOT NULL,
    balance_minor_units numeric     NOT NULL,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT ck_account_balance_non_negative CHECK (balance_minor_units >= 0),
    CONSTRAINT ck_account_balance_whole CHECK (balance_minor_units = trunc(balance_minor_units))
);
--rollback DROP TABLE account;

--changeset taraz:0001-account-storage
--comment ADR-0019: balance is never indexed, so every UPDATE is HOT-eligible; leave room on the page.
--  Tighter autovacuum than default — this row is rewritten on every credit/debit.
ALTER TABLE account SET (
    fillfactor = 85,
    autovacuum_vacuum_scale_factor = 0.02,
    autovacuum_analyze_scale_factor = 0.02
);
--rollback ALTER TABLE account RESET (fillfactor, autovacuum_vacuum_scale_factor, autovacuum_analyze_scale_factor);
