-- =====================================================================================================
-- V17 Maker-checker approvals.
--
-- An approval request holds an action one person (the maker) asked for and another (the checker) must approve
-- before it runs: transaction reversals and manual journals always, withdrawals and transfers above an
-- institution's thresholds. The database enforces four eyes and that a decided request never changes.
-- =====================================================================================================

CREATE TABLE core.approval_policy
(
    tenant_id        uuid           NOT NULL,
    request_type     varchar(30)    NOT NULL,
    currency         varchar(3)     NOT NULL,
    threshold_amount numeric(19, 4) NOT NULL,
    active           boolean        NOT NULL,
    updated_at       timestamptz    NOT NULL,
    updated_by       uuid,
    CONSTRAINT pk_approval_policy PRIMARY KEY (tenant_id, request_type, currency),
    CONSTRAINT fk_approval_policy_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_approval_policy_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT ck_approval_policy_type CHECK (request_type IN ('CASH_WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_approval_policy_threshold CHECK (threshold_amount > 0)
);

SELECT core.apply_tenant_isolation('core.approval_policy');
GRANT SELECT, INSERT ON core.approval_policy TO ${app_db_role};
GRANT UPDATE (threshold_amount, active, updated_at, updated_by) ON core.approval_policy TO ${app_db_role};

CREATE TABLE core.approval_request
(
    id                 uuid           NOT NULL,
    tenant_id          uuid           NOT NULL,
    request_type       varchar(30)    NOT NULL,
    status             varchar(10)    NOT NULL,
    branch_id          uuid           NOT NULL,
    amount             numeric(19, 4),
    currency           varchar(3),
    resource_type      varchar(40),
    resource_id        uuid,
    summary            varchar(300)   NOT NULL,
    payload            jsonb          NOT NULL,
    requested_by       uuid           NOT NULL,
    requested_at       timestamptz    NOT NULL,
    decided_by         uuid,
    decided_at         timestamptz,
    decision_note      varchar(300),
    result_resource_id uuid,
    version            bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_approval_request PRIMARY KEY (id),
    CONSTRAINT uq_approval_request_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_approval_request_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_approval_request_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_approval_request_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT ck_approval_request_type
        CHECK (request_type IN ('TRANSACTION_REVERSAL', 'MANUAL_JOURNAL', 'CASH_WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_approval_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_approval_request_amount CHECK ((amount IS NULL) = (currency IS NULL) AND (amount IS NULL OR amount > 0)),
    CONSTRAINT ck_approval_request_decided CHECK ((status = 'PENDING') = (decided_at IS NULL)),
    CONSTRAINT ck_approval_request_four_eyes CHECK (decided_by IS NULL OR decided_by <> requested_by
        OR status = 'CANCELLED'),
    CONSTRAINT ck_approval_request_result CHECK (result_resource_id IS NULL OR status = 'APPROVED')
);

CREATE INDEX ix_approval_request_queue ON core.approval_request (tenant_id, status, branch_id, requested_at);
-- One open request per resource and kind (e.g. a transaction cannot have two pending reversals).
CREATE UNIQUE INDEX uq_approval_request_pending_resource ON core.approval_request (tenant_id, request_type, resource_id)
    WHERE status = 'PENDING' AND resource_id IS NOT NULL;
SELECT core.apply_tenant_isolation('core.approval_request');

-- A request is decided once; what was asked for never changes.
CREATE FUNCTION core.approval_request_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.status <> 'PENDING' THEN
        RAISE EXCEPTION 'A decided approval request cannot change' USING ERRCODE = 'check_violation';
    END IF;
    IF (to_jsonb(NEW) - ARRAY ['status', 'decided_by', 'decided_at', 'decision_note', 'result_resource_id',
        'version'])
        <> (to_jsonb(OLD) - ARRAY ['status', 'decided_by', 'decided_at', 'decision_note', 'result_resource_id',
        'version']) THEN
        RAISE EXCEPTION 'Only the decision of an approval request can be recorded' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_approval_request_guard
    BEFORE UPDATE ON core.approval_request
    FOR EACH ROW EXECUTE FUNCTION core.approval_request_guard();
CREATE TRIGGER trg_approval_request_no_delete
    BEFORE DELETE ON core.approval_request
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();

GRANT SELECT, INSERT ON core.approval_request TO ${app_db_role};
GRANT UPDATE (status, decided_by, decided_at, decision_note, result_resource_id, version)
    ON core.approval_request TO ${app_db_role};

-- Transactions that went through maker-checker name their approver and request.
ALTER TABLE core.financial_transaction
    ADD COLUMN approved_by         uuid,
    ADD COLUMN approval_request_id uuid,
    ADD CONSTRAINT fk_financial_transaction_approval FOREIGN KEY (tenant_id, approval_request_id)
        REFERENCES core.approval_request (tenant_id, id),
    ADD CONSTRAINT ck_financial_transaction_approval CHECK ((approved_by IS NULL) = (approval_request_id IS NULL)),
    ADD CONSTRAINT ck_financial_transaction_four_eyes CHECK (approved_by IS NULL OR approved_by <> initiated_by);
