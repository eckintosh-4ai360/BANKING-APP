-- =====================================================================================================
-- V3 Branches
-- =====================================================================================================

CREATE TABLE core.branch
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    code            varchar(20)  NOT NULL,
    name            varchar(120) NOT NULL,
    branch_type     varchar(20)  NOT NULL,
    status          varchar(20)  NOT NULL,
    phone           varchar(20),
    email           varchar(254),
    address_line1   varchar(200),
    address_line2   varchar(200),
    city            varchar(100),
    region          varchar(100),
    digital_address varchar(20),
    opened_on       date,
    closed_on       date,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_branch PRIMARY KEY (id),
    -- Target for composite (tenant_id, branch_id) foreign keys from child tables.
    CONSTRAINT uq_branch_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_branch_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_branch_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_branch_code CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,19}$'),
    CONSTRAINT ck_branch_type CHECK (branch_type IN ('HEAD_OFFICE', 'BRANCH', 'AGENCY')),
    CONSTRAINT ck_branch_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED')),
    CONSTRAINT ck_branch_closed_on CHECK ((status = 'CLOSED') = (closed_on IS NOT NULL))
);

-- At most one non-closed head office per tenant.
CREATE UNIQUE INDEX uq_branch_one_head_office ON core.branch (tenant_id)
    WHERE branch_type = 'HEAD_OFFICE' AND status <> 'CLOSED';
CREATE INDEX ix_branch_tenant_status ON core.branch (tenant_id, status);

SELECT core.apply_tenant_isolation('core.branch');
GRANT SELECT, INSERT, UPDATE ON core.branch TO ${app_db_role};
