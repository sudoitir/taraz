--liquibase formatted sql

--changeset taraz:0007-shedlock-create
--comment ADR-0057: ShedLock's table, coordinating the outbox poller/cleanup across horizontally-scaled
--  pods so exactly one instance runs a given scheduled task at a time. Column order/types match
--  ShedLock's own required JDBC schema exactly (net.javacrumbs.shedlock.provider.jdbctemplate) —
--  do not rename columns without updating JdbcTemplateLockProvider's configuration to match.
CREATE TABLE shedlock (
    name       varchar(64)  NOT NULL,
    lock_until timestamp(3) NOT NULL,
    locked_at  timestamp(3) NOT NULL,
    locked_by  varchar(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
--rollback DROP TABLE shedlock;
