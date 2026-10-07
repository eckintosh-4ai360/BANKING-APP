-- =====================================================================================================
-- V5 Audit log: append-only record of security and business events.
-- No foreign keys point into or out of this table so it can be range-partitioned by occurred_at later.
-- =====================================================================================================

CREATE TABLE core.audit_log
(
    id                 uuid         NOT NULL,
    tenant_id          uuid,
    occurred_at        timestamptz  NOT NULL,
    actor_type         varchar(20)  NOT NULL,
    actor_id           uuid,
    actor_name         varchar(200),
    action             varchar(80)  NOT NULL,
    outcome            varchar(10)  NOT NULL,
    resource_type      varchar(60)  NOT NULL,
    resource_id        varchar(100),
    resource_reference varchar(100),
    branch_id          uuid,
    before_state       jsonb,
    after_state        jsonb,
    metadata           jsonb,
    ip_address         varchar(45),
    user_agent         varchar(400),
    device_id          varchar(100),
    correlation_id     varchar(64),
    CONSTRAINT pk_audit_log PRIMARY KEY (id),
    CONSTRAINT ck_audit_log_actor_type CHECK (actor_type IN
                                              ('STAFF', 'CUSTOMER', 'PLATFORM_ADMIN', 'SYSTEM', 'ANONYMOUS')),
    CONSTRAINT ck_audit_log_action CHECK (action ~ '^[A-Z][A-Z0-9_]+$'),
    CONSTRAINT ck_audit_log_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

CREATE INDEX ix_audit_log_tenant_occurred ON core.audit_log (tenant_id, occurred_at DESC);
CREATE INDEX ix_audit_log_tenant_resource ON core.audit_log (tenant_id, resource_type, resource_id);
CREATE INDEX ix_audit_log_tenant_actor ON core.audit_log (tenant_id, actor_id, occurred_at DESC);
CREATE INDEX ix_audit_log_correlation ON core.audit_log (correlation_id);

-- Immutability: rejected for every role, including the owner.
CREATE TRIGGER trg_audit_log_immutable
    BEFORE UPDATE OR DELETE
    ON core.audit_log
    FOR EACH ROW
EXECUTE FUNCTION core.reject_mutation();

CREATE TRIGGER trg_audit_log_no_truncate
    BEFORE TRUNCATE
    ON core.audit_log
    FOR EACH STATEMENT
EXECUTE FUNCTION core.reject_mutation();

SELECT core.apply_tenant_or_platform_isolation('core.audit_log');
-- The runtime role can only append and read.
GRANT SELECT, INSERT ON core.audit_log TO ${app_db_role};
