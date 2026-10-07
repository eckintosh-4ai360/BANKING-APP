-- =====================================================================================================
-- V6 Gap-free per-tenant counters for human-facing numbers (customer numbers, account numbers).
-- =====================================================================================================

CREATE TABLE core.number_sequence
(
    tenant_id    uuid        NOT NULL,
    sequence_key varchar(50) NOT NULL,
    next_value   bigint      NOT NULL,
    updated_at   timestamptz NOT NULL,
    CONSTRAINT pk_number_sequence PRIMARY KEY (tenant_id, sequence_key),
    CONSTRAINT fk_number_sequence_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_number_sequence_next_value CHECK (next_value >= 1)
);

SELECT core.apply_tenant_isolation('core.number_sequence');
GRANT SELECT, INSERT, UPDATE ON core.number_sequence TO ${app_db_role};
