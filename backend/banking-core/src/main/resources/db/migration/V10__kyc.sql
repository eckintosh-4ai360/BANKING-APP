-- =====================================================================================================
-- V10 KYC cases (maker-checker decisions) and checks (immutable evidence).
-- =====================================================================================================

CREATE TABLE core.kyc_case
(
    id                  uuid        NOT NULL,
    tenant_id           uuid        NOT NULL,
    customer_id         uuid        NOT NULL,
    -- Customer's home branch when the case was opened: lets review queues be filtered by branch scope.
    branch_id           uuid        NOT NULL,
    case_type           varchar(20) NOT NULL,
    target_tier_code    varchar(30) NOT NULL,
    status              varchar(20) NOT NULL,
    opened_by           uuid,
    submitted_by        uuid,
    submitted_at        timestamptz,
    decided_by          uuid,
    decided_at          timestamptz,
    decision_note       varchar(500),
    assigned_risk_level varchar(20),
    created_at          timestamptz NOT NULL,
    updated_at          timestamptz NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    version             bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_kyc_case PRIMARY KEY (id),
    CONSTRAINT uq_kyc_case_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_kyc_case_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_kyc_case_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_kyc_case_tier FOREIGN KEY (tenant_id, target_tier_code) REFERENCES core.kyc_tier (tenant_id, code),
    CONSTRAINT ck_kyc_case_type CHECK (case_type IN ('ONBOARDING', 'PERIODIC_REVIEW', 'UPGRADE', 'UPDATE')),
    CONSTRAINT ck_kyc_case_status CHECK (status IN
                                         ('OPEN', 'PENDING_REVIEW', 'RETURNED', 'APPROVED', 'REJECTED',
                                          'CANCELLED')),
    CONSTRAINT ck_kyc_case_risk CHECK (assigned_risk_level IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_kyc_case_decision CHECK ((status IN ('APPROVED', 'REJECTED')) = (decided_by IS NOT NULL)),
    -- Four-eyes principle, enforced by the database: whoever submitted a case can never decide it.
    CONSTRAINT ck_kyc_case_four_eyes CHECK (decided_by IS NULL OR decided_by <> submitted_by)
);

CREATE INDEX ix_kyc_case_tenant_status ON core.kyc_case (tenant_id, status, branch_id);
CREATE INDEX ix_kyc_case_customer ON core.kyc_case (tenant_id, customer_id);
CREATE UNIQUE INDEX uq_kyc_case_one_open ON core.kyc_case (customer_id)
    WHERE status IN ('OPEN', 'PENDING_REVIEW', 'RETURNED');

SELECT core.apply_tenant_isolation('core.kyc_case');
GRANT SELECT, INSERT, UPDATE ON core.kyc_case TO ${app_db_role};


CREATE TABLE core.kyc_check
(
    id                 uuid        NOT NULL,
    tenant_id          uuid        NOT NULL,
    kyc_case_id        uuid        NOT NULL,
    check_type         varchar(30) NOT NULL,
    method             varchar(20) NOT NULL,
    provider           varchar(50),
    result             varchar(20) NOT NULL,
    score              numeric(5, 2),
    provider_reference varchar(100),
    note               varchar(500),
    performed_by       uuid,
    performed_at       timestamptz NOT NULL,
    CONSTRAINT pk_kyc_check PRIMARY KEY (id),
    CONSTRAINT fk_kyc_check_case FOREIGN KEY (tenant_id, kyc_case_id) REFERENCES core.kyc_case (tenant_id, id),
    CONSTRAINT ck_kyc_check_type CHECK (check_type IN
                                        ('IDENTITY_VERIFICATION', 'WATCHLIST', 'PEP', 'FACE_MATCH', 'ADDRESS',
                                         'PHONE', 'DOCUMENT')),
    CONSTRAINT ck_kyc_check_method CHECK (method IN ('ELECTRONIC', 'MANUAL')),
    CONSTRAINT ck_kyc_check_result CHECK (result IN ('PASS', 'FAIL', 'INCONCLUSIVE', 'ERROR'))
);

CREATE INDEX ix_kyc_check_case ON core.kyc_check (tenant_id, kyc_case_id);

-- Checks are evidence: append-only.
CREATE TRIGGER trg_kyc_check_immutable
    BEFORE UPDATE OR DELETE
    ON core.kyc_check
    FOR EACH ROW
EXECUTE FUNCTION core.reject_mutation();

SELECT core.apply_tenant_isolation('core.kyc_check');
GRANT SELECT, INSERT ON core.kyc_check TO ${app_db_role};
