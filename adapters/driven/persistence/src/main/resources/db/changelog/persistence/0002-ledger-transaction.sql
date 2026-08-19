--liquibase formatted sql

--changeset taraz:0002-ledger-transaction-create
--comment ADR-0016/0037/0047: surrogate UUIDv7 PK; external_id is the client TransactionId
--  (== REST Idempotency-Key, ADR-0043). Named external_id, not transaction_id, so it can never be
--  confused with processed_transaction's PK column of the latter name (ADR-0047).
CREATE TABLE ledger_transaction (
    occurred_at              timestamptz NOT NULL,
    created_at               timestamptz NOT NULL,
    id                       uuid        NOT NULL,
    type                     varchar(16) NOT NULL,
    status                   varchar(16) NOT NULL,
    external_id              varchar(64) NOT NULL,
    compensates_external_id  varchar(64),
    CONSTRAINT pk_ledger_transaction PRIMARY KEY (id),
    CONSTRAINT uq_ledger_transaction_external_id UNIQUE (external_id),
    CONSTRAINT ck_ledger_transaction_type CHECK (type IN ('CREDIT', 'DEBIT', 'TRANSFER')),
    CONSTRAINT ck_ledger_transaction_status CHECK (status IN ('APPLIED', 'REJECTED', 'COMPENSATED'))
);
--rollback DROP TABLE ledger_transaction;
