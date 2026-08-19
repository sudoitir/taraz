--liquibase formatted sql

--changeset taraz:0006-session-timeouts runOnChange:true
--comment ADR-0046: lock_timeout < statement_timeout < idle_in_transaction_session_timeout, applied
--  at the role so every session (app, psql, liquibase) agrees. A lock wait then fails with the
--  translatable SQLState 55P03, not the generic 57014 a slow query would also produce.
ALTER ROLE CURRENT_USER SET lock_timeout = '3s';
ALTER ROLE CURRENT_USER SET statement_timeout = '10s';
ALTER ROLE CURRENT_USER SET idle_in_transaction_session_timeout = '15s';
--rollback ALTER ROLE CURRENT_USER RESET lock_timeout; ALTER ROLE CURRENT_USER RESET statement_timeout; ALTER ROLE CURRENT_USER RESET idle_in_transaction_session_timeout;
