-- =====================================================================================================
-- V28 Loan collections, restructure and write-off.
--
-- Collections activity: calls, visits and promises to pay, kept per loan; end-of-day marks a promise kept or broken
-- on its date. Restructure and write-off are maker-checker approvals. A restructure adds a schedule version for the
-- principal still owed: the interest and penalties already earned are carried into its first installment, and the
-- loan keeps its delinquency band for as long as the approver decided. A write-off takes the loan off the books
-- against its provision; money recovered later is income.
-- =====================================================================================================

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('loan.collect', 'loan', 'Record collection calls, visits and promises to pay', 'TENANT', false),
       ('loan.restructure', 'loan', 'Ask for a loan to be restructured', 'TENANT', true);

ALTER TABLE core.approval_request DROP CONSTRAINT ck_approval_request_type;
ALTER TABLE core.approval_request ADD CONSTRAINT ck_approval_request_type
    CHECK (request_type IN ('TRANSACTION_REVERSAL', 'MANUAL_JOURNAL', 'CASH_WITHDRAWAL', 'TRANSFER',
                            'LOAN_RESTRUCTURE', 'LOAN_WRITE_OFF'));

CREATE TABLE core.loan_collection_activity
(
    id              uuid           NOT NULL,
    tenant_id       uuid           NOT NULL,
    loan_id         uuid           NOT NULL,
    activity_type   varchar(10)    NOT NULL,
    note            varchar(1000)  NOT NULL,
    days_past_due   integer        NOT NULL,
    promised_amount numeric(19, 4),
    promised_date   date,
    promise_status  varchar(10),
    business_date   date           NOT NULL,
    created_by      uuid           NOT NULL,
    created_at      timestamptz    NOT NULL,
    CONSTRAINT pk_loan_collection_activity PRIMARY KEY (id),
    CONSTRAINT fk_loan_collection_activity_loan FOREIGN KEY (tenant_id, loan_id) REFERENCES core.loan (tenant_id, id),
    CONSTRAINT ck_loan_collection_activity_type CHECK (activity_type IN ('CALL', 'VISIT', 'SMS', 'LETTER',
        'PROMISE', 'OTHER')),
    CONSTRAINT ck_loan_collection_activity_promise CHECK ((activity_type = 'PROMISE') = (promised_amount IS NOT NULL)
        AND (promised_amount IS NULL) = (promised_date IS NULL)
        AND (promised_amount IS NULL) = (promise_status IS NULL)
        AND (promised_amount IS NULL OR promised_amount > 0)
        AND (promise_status IS NULL OR promise_status IN ('OPEN', 'KEPT', 'BROKEN'))
        AND (promised_date IS NULL OR promised_date >= business_date))
);

CREATE INDEX ix_loan_collection_activity_loan ON core.loan_collection_activity (tenant_id, loan_id, created_at);
CREATE INDEX ix_loan_collection_activity_open ON core.loan_collection_activity (tenant_id, loan_id, promised_date)
    WHERE promise_status = 'OPEN';
SELECT core.apply_tenant_isolation('core.loan_collection_activity');
CREATE TRIGGER trg_loan_collection_activity_no_delete
    BEFORE DELETE ON core.loan_collection_activity
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan_collection_activity TO ${app_db_role};
GRANT UPDATE (promise_status) ON core.loan_collection_activity TO ${app_db_role};

-- The current schedule's start (disbursement, or the restructure date), the band a restructured loan keeps until a
-- date, and what was written off and recovered.
ALTER TABLE core.loan
    ADD COLUMN schedule_start_date       date,
    ADD COLUMN band_floor                varchar(20),
    ADD COLUMN band_floor_until          date,
    ADD COLUMN written_off_principal     numeric(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN written_off_interest      numeric(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN written_off_penalty       numeric(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN recovered                 numeric(19, 4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_loan_band_floor CHECK ((band_floor IS NULL) = (band_floor_until IS NULL)),
    ADD CONSTRAINT ck_loan_write_off CHECK (written_off_principal >= 0 AND written_off_interest >= 0
        AND written_off_penalty >= 0 AND recovered >= 0
        AND recovered <= written_off_principal + written_off_interest + written_off_penalty);
UPDATE core.loan SET schedule_start_date = disbursement_date;
ALTER TABLE core.loan ALTER COLUMN schedule_start_date SET NOT NULL;
GRANT UPDATE (schedule_start_date, band_floor, band_floor_until, installments, first_due_date, maturity_date,
    written_off_principal, written_off_interest, written_off_penalty, recovered) ON core.loan TO ${app_db_role};

-- Interest already recognised and carried into a restructured schedule's first installment (not earned again).
ALTER TABLE core.loan_installment
    ADD COLUMN interest_carried numeric(19, 4) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_loan_installment_carried CHECK (interest_carried BETWEEN 0 AND interest_due);

CREATE TABLE core.loan_restructure
(
    id                  uuid           NOT NULL,
    tenant_id           uuid           NOT NULL,
    loan_id             uuid           NOT NULL,
    from_version        integer        NOT NULL,
    to_version          integer        NOT NULL,
    principal           numeric(19, 4) NOT NULL,
    interest_carried    numeric(19, 4) NOT NULL,
    penalty_carried     numeric(19, 4) NOT NULL,
    installments        integer        NOT NULL,
    first_due_date      date           NOT NULL,
    band_at_restructure varchar(20),
    hold_band_days      integer        NOT NULL,
    reason              varchar(300)   NOT NULL,
    requested_by        uuid           NOT NULL,
    approved_by         uuid           NOT NULL,
    approval_request_id uuid           NOT NULL,
    business_date       date           NOT NULL,
    created_at          timestamptz    NOT NULL,
    CONSTRAINT pk_loan_restructure PRIMARY KEY (id),
    CONSTRAINT uq_loan_restructure_version UNIQUE (tenant_id, loan_id, to_version),
    CONSTRAINT fk_loan_restructure_loan FOREIGN KEY (tenant_id, loan_id) REFERENCES core.loan (tenant_id, id),
    CONSTRAINT ck_loan_restructure_values CHECK (to_version = from_version + 1 AND principal > 0
        AND interest_carried >= 0 AND penalty_carried >= 0 AND installments > 0
        AND hold_band_days BETWEEN 0 AND 730 AND approved_by <> requested_by)
);

SELECT core.apply_tenant_isolation('core.loan_restructure');
CREATE TRIGGER trg_loan_restructure_immutable
    BEFORE UPDATE OR DELETE ON core.loan_restructure
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan_restructure TO ${app_db_role};

CREATE TABLE core.loan_recovery
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    loan_id                  uuid           NOT NULL,
    financial_transaction_id uuid           NOT NULL,
    source                   varchar(10)    NOT NULL,
    amount                   numeric(19, 4) NOT NULL,
    business_date            date           NOT NULL,
    received_by              uuid,
    created_at               timestamptz    NOT NULL,
    CONSTRAINT pk_loan_recovery PRIMARY KEY (id),
    CONSTRAINT uq_loan_recovery_transaction UNIQUE (tenant_id, financial_transaction_id),
    CONSTRAINT fk_loan_recovery_loan FOREIGN KEY (tenant_id, loan_id) REFERENCES core.loan (tenant_id, id),
    CONSTRAINT fk_loan_recovery_transaction FOREIGN KEY (tenant_id, financial_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id),
    CONSTRAINT ck_loan_recovery_source CHECK (source IN ('ACCOUNT', 'CASH')),
    CONSTRAINT ck_loan_recovery_amount CHECK (amount > 0)
);

SELECT core.apply_tenant_isolation('core.loan_recovery');
CREATE TRIGGER trg_loan_recovery_immutable
    BEFORE UPDATE OR DELETE ON core.loan_recovery
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan_recovery TO ${app_db_role};

CREATE INDEX ix_loan_arrears ON core.loan (tenant_id, days_past_due) WHERE status = 'ACTIVE' AND days_past_due > 0;
