-- =====================================================================================================
-- V26 Loans: products and their versioned terms, applications with their workflow, guarantors and collateral,
-- loans with their schedules, and repayments.
--
-- A loan snapshots its terms at disbursement (a product change never rewrites a contract). Its balances are its
-- ledger accounts (principal, interest receivable, penalty receivable): the schedule is the contractual plan and
-- the record of what each repayment settled, not the balance of record.
-- =====================================================================================================

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('loan.repay', 'loan', 'Take loan repayments from accounts or in cash', 'TENANT', true);

-- Loan money movements are customer transactions: a disbursement credits the borrower's account; a repayment
-- debits it, or is paid in cash at a till (no customer account).
ALTER TABLE core.financial_transaction DROP CONSTRAINT ck_financial_transaction_type;
ALTER TABLE core.financial_transaction ADD CONSTRAINT ck_financial_transaction_type
    CHECK (transaction_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'TRANSFER', 'FIELD_COLLECTION',
                                'LOAN_DISBURSEMENT', 'LOAN_REPAYMENT'));
ALTER TABLE core.financial_transaction DROP CONSTRAINT ck_financial_transaction_accounts;
ALTER TABLE core.financial_transaction ADD CONSTRAINT ck_financial_transaction_accounts CHECK (
    (transaction_type IN ('CASH_DEPOSIT', 'FIELD_COLLECTION', 'LOAN_DISBURSEMENT') AND credit_account_id IS NOT NULL
        AND debit_account_id IS NULL)
        OR (transaction_type = 'CASH_WITHDRAWAL' AND debit_account_id IS NOT NULL AND credit_account_id IS NULL)
        OR (transaction_type = 'LOAN_REPAYMENT' AND credit_account_id IS NULL)
        OR (transaction_type = 'TRANSFER' AND debit_account_id IS NOT NULL AND credit_account_id IS NOT NULL
        AND debit_account_id <> credit_account_id));

CREATE TABLE core.loan_product
(
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    code        varchar(30)  NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(500),
    status      varchar(10)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_loan_product PRIMARY KEY (id),
    CONSTRAINT uq_loan_product_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loan_product_code UNIQUE (tenant_id, code),
    CONSTRAINT ck_loan_product_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,29}$'),
    CONSTRAINT ck_loan_product_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

SELECT core.apply_tenant_isolation('core.loan_product');
GRANT SELECT, INSERT ON core.loan_product TO ${app_db_role};
GRANT UPDATE (name, description, status, updated_at, version) ON core.loan_product TO ${app_db_role};

CREATE TABLE core.loan_product_version
(
    id                            uuid           NOT NULL,
    tenant_id                     uuid           NOT NULL,
    product_id                    uuid           NOT NULL,
    version_no                    integer        NOT NULL,
    status                        varchar(10)    NOT NULL,
    currency                      varchar(3)     NOT NULL,
    min_amount                    numeric(19, 4) NOT NULL,
    max_amount                    numeric(19, 4) NOT NULL,
    min_installments              integer        NOT NULL,
    max_installments              integer        NOT NULL,
    interest_method               varchar(40)    NOT NULL,
    annual_rate                   numeric(9, 6)  NOT NULL,
    day_count                     varchar(12)    NOT NULL,
    repayment_frequency           varchar(10)    NOT NULL,
    principal_grace               integer        NOT NULL DEFAULT 0,
    interest_grace                integer        NOT NULL DEFAULT 0,
    rounding_mode                 varchar(10)    NOT NULL,
    allocation_order              varchar(40)    NOT NULL,
    processing_fee_rate           numeric(9, 6)  NOT NULL DEFAULT 0,
    processing_fee_flat           numeric(19, 4) NOT NULL DEFAULT 0,
    penalty_rate                  numeric(9, 6)  NOT NULL DEFAULT 0,
    penalty_grace_days            integer        NOT NULL DEFAULT 0,
    required_guarantors           integer        NOT NULL DEFAULT 0,
    collateral_coverage           numeric(9, 4)  NOT NULL DEFAULT 0,
    second_approval_above         numeric(19, 4),
    required_kyc_tier             varchar(20),
    principal_gl_id               uuid           NOT NULL,
    interest_receivable_gl_id     uuid           NOT NULL,
    interest_income_gl_id         uuid           NOT NULL,
    fee_income_gl_id              uuid           NOT NULL,
    penalty_receivable_gl_id      uuid           NOT NULL,
    penalty_income_gl_id          uuid           NOT NULL,
    created_at                    timestamptz    NOT NULL,
    published_at                  timestamptz,
    CONSTRAINT pk_loan_product_version PRIMARY KEY (id),
    CONSTRAINT uq_loan_product_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loan_product_version_no UNIQUE (product_id, version_no),
    CONSTRAINT fk_loan_product_version_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES core.loan_product (tenant_id, id),
    CONSTRAINT fk_loan_product_version_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_loan_product_version_principal_gl FOREIGN KEY (tenant_id, principal_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_loan_product_version_interest_gl FOREIGN KEY (tenant_id, interest_receivable_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_loan_product_version_income_gl FOREIGN KEY (tenant_id, interest_income_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_loan_product_version_fee_gl FOREIGN KEY (tenant_id, fee_income_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_loan_product_version_penalty_gl FOREIGN KEY (tenant_id, penalty_receivable_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_loan_product_version_penalty_income_gl FOREIGN KEY (tenant_id, penalty_income_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT ck_loan_product_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')
        AND (status = 'DRAFT') = (published_at IS NULL)),
    CONSTRAINT ck_loan_product_version_amounts CHECK (min_amount > 0 AND max_amount >= min_amount
        AND processing_fee_flat >= 0 AND (second_approval_above IS NULL OR second_approval_above > 0)),
    CONSTRAINT ck_loan_product_version_installments CHECK (min_installments >= 1
        AND max_installments >= min_installments AND max_installments <= 520
        AND principal_grace >= 0 AND interest_grace >= 0 AND interest_grace <= principal_grace
        AND principal_grace < min_installments),
    CONSTRAINT ck_loan_product_version_rates CHECK (annual_rate BETWEEN 0 AND 1000
        AND processing_fee_rate BETWEEN 0 AND 100 AND penalty_rate BETWEEN 0 AND 1000
        AND penalty_grace_days BETWEEN 0 AND 365 AND collateral_coverage BETWEEN 0 AND 1000
        AND required_guarantors BETWEEN 0 AND 10),
    CONSTRAINT ck_loan_product_version_method CHECK (interest_method IN ('FLAT',
        'DECLINING_BALANCE_EQUAL_INSTALLMENT', 'DECLINING_BALANCE_EQUAL_PRINCIPAL')),
    CONSTRAINT ck_loan_product_version_day_count CHECK (day_count IN ('ACTUAL_365F', 'ACTUAL_360', 'THIRTY_360')),
    CONSTRAINT ck_loan_product_version_frequency CHECK (repayment_frequency IN ('DAILY', 'WEEKLY', 'BIWEEKLY',
        'MONTHLY', 'QUARTERLY')),
    CONSTRAINT ck_loan_product_version_rounding CHECK (rounding_mode IN ('HALF_EVEN', 'HALF_UP')),
    CONSTRAINT ck_loan_product_version_allocation CHECK (allocation_order ~
        '^(PENALTY|FEE|INTEREST|PRINCIPAL)(,(PENALTY|FEE|INTEREST|PRINCIPAL)){3}$')
);

-- One published version per product.
CREATE UNIQUE INDEX uq_loan_product_version_published ON core.loan_product_version (tenant_id, product_id)
    WHERE status = 'PUBLISHED';
SELECT core.apply_tenant_isolation('core.loan_product_version');

-- Published terms never change (applications and loans refer to them).
CREATE FUNCTION core.loan_product_version_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.status <> 'DRAFT' AND ((to_jsonb(NEW) - ARRAY ['status']) <> (to_jsonb(OLD) - ARRAY ['status'])
        OR NOT (OLD.status = 'PUBLISHED' AND NEW.status IN ('PUBLISHED', 'RETIRED'))) THEN
        RAISE EXCEPTION 'Published loan terms cannot change' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loan_product_version_guard
    BEFORE UPDATE ON core.loan_product_version
    FOR EACH ROW EXECUTE FUNCTION core.loan_product_version_guard();
GRANT SELECT, INSERT, UPDATE ON core.loan_product_version TO ${app_db_role};

CREATE TABLE core.loan_application
(
    id                      uuid           NOT NULL,
    tenant_id               uuid           NOT NULL,
    application_number      varchar(30)    NOT NULL,
    customer_id             uuid           NOT NULL,
    branch_id               uuid           NOT NULL,
    product_id              uuid           NOT NULL,
    product_version_id      uuid           NOT NULL,
    currency                varchar(3)     NOT NULL,
    requested_amount        numeric(19, 4) NOT NULL,
    requested_installments  integer        NOT NULL,
    purpose                 varchar(300)   NOT NULL,
    monthly_income          numeric(19, 4),
    monthly_expenses        numeric(19, 4),
    existing_debt           numeric(19, 4),
    disbursement_account_id uuid           NOT NULL,
    status                  varchar(12)    NOT NULL,
    risk_rating             varchar(10),
    assessment_note         varchar(1000),
    approved_amount         numeric(19, 4),
    approved_installments   integer,
    first_due_date          date,
    loan_officer_id         uuid           NOT NULL,
    created_at              timestamptz    NOT NULL,
    updated_at              timestamptz    NOT NULL,
    version                 bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_loan_application PRIMARY KEY (id),
    CONSTRAINT uq_loan_application_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loan_application_number UNIQUE (tenant_id, application_number),
    CONSTRAINT fk_loan_application_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_loan_application_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_loan_application_product_version FOREIGN KEY (tenant_id, product_version_id)
        REFERENCES core.loan_product_version (tenant_id, id),
    CONSTRAINT fk_loan_application_account FOREIGN KEY (tenant_id, disbursement_account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_loan_application_officer FOREIGN KEY (tenant_id, loan_officer_id)
        REFERENCES core.staff (tenant_id, id),
    CONSTRAINT ck_loan_application_status CHECK (status IN ('DRAFT', 'SUBMITTED', 'ASSESSED', 'RECOMMENDED',
        'APPROVED', 'REJECTED', 'WITHDRAWN', 'DISBURSED')),
    CONSTRAINT ck_loan_application_amounts CHECK (requested_amount > 0 AND requested_installments > 0
        AND (approved_amount IS NULL OR approved_amount > 0) AND (approved_installments IS NULL
        OR approved_installments > 0)),
    CONSTRAINT ck_loan_application_risk CHECK (risk_rating IS NULL OR risk_rating IN ('LOW', 'MEDIUM', 'HIGH'))
);

CREATE INDEX ix_loan_application_customer ON core.loan_application (tenant_id, customer_id);
CREATE INDEX ix_loan_application_status ON core.loan_application (tenant_id, status, branch_id);
SELECT core.apply_tenant_isolation('core.loan_application');
CREATE TRIGGER trg_loan_application_no_delete
    BEFORE DELETE ON core.loan_application
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT, UPDATE ON core.loan_application TO ${app_db_role};

-- Every workflow step, in order, by whom. Separation of duties is enforced here too: whoever recommends, approves
-- or disburses must be someone else than the loan officer and than everyone who took another of those steps.
CREATE TABLE core.loan_application_step
(
    id             uuid          NOT NULL,
    tenant_id      uuid          NOT NULL,
    application_id uuid          NOT NULL,
    step_type      varchar(15)   NOT NULL,
    actor_id       uuid          NOT NULL,
    occurred_at    timestamptz   NOT NULL,
    note           varchar(1000),
    CONSTRAINT pk_loan_application_step PRIMARY KEY (id),
    CONSTRAINT fk_loan_application_step_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES core.loan_application (tenant_id, id),
    CONSTRAINT ck_loan_application_step_type CHECK (step_type IN ('SUBMIT', 'ASSESS', 'RECOMMEND', 'APPROVE',
        'SECOND_APPROVE', 'REJECT', 'WITHDRAW', 'DISBURSE'))
);

CREATE UNIQUE INDEX uq_loan_application_step_once ON core.loan_application_step (application_id, step_type)
    WHERE step_type IN ('SUBMIT', 'RECOMMEND', 'APPROVE', 'SECOND_APPROVE', 'REJECT', 'WITHDRAW', 'DISBURSE');
CREATE UNIQUE INDEX uq_loan_application_step_four_eyes ON core.loan_application_step (application_id, actor_id)
    WHERE step_type IN ('RECOMMEND', 'APPROVE', 'SECOND_APPROVE', 'DISBURSE');

CREATE FUNCTION core.loan_application_step_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF NEW.step_type IN ('RECOMMEND', 'APPROVE', 'SECOND_APPROVE', 'DISBURSE') AND EXISTS (
        SELECT 1 FROM core.loan_application a WHERE a.id = NEW.application_id AND a.loan_officer_id = NEW.actor_id) THEN
        RAISE EXCEPTION 'The loan officer cannot recommend, approve or disburse their own application'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_loan_application_step_guard
    BEFORE INSERT ON core.loan_application_step
    FOR EACH ROW EXECUTE FUNCTION core.loan_application_step_guard();
CREATE TRIGGER trg_loan_application_step_immutable
    BEFORE UPDATE OR DELETE ON core.loan_application_step
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
SELECT core.apply_tenant_isolation('core.loan_application_step');
GRANT SELECT, INSERT ON core.loan_application_step TO ${app_db_role};

CREATE TABLE core.loan_guarantor
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    application_id    uuid           NOT NULL,
    customer_id       uuid,
    full_name         varchar(150)   NOT NULL,
    phone             varchar(30),
    relationship      varchar(60)    NOT NULL,
    guaranteed_amount numeric(19, 4) NOT NULL,
    verified_by       uuid,
    verified_at       timestamptz,
    created_at        timestamptz    NOT NULL,
    CONSTRAINT pk_loan_guarantor PRIMARY KEY (id),
    CONSTRAINT fk_loan_guarantor_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES core.loan_application (tenant_id, id),
    CONSTRAINT fk_loan_guarantor_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_loan_guarantor_amount CHECK (guaranteed_amount > 0),
    CONSTRAINT ck_loan_guarantor_verified CHECK ((verified_by IS NULL) = (verified_at IS NULL))
);

SELECT core.apply_tenant_isolation('core.loan_guarantor');
GRANT SELECT, INSERT ON core.loan_guarantor TO ${app_db_role};
GRANT UPDATE (verified_by, verified_at) ON core.loan_guarantor TO ${app_db_role};

CREATE TABLE core.loan_collateral
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    application_id    uuid           NOT NULL,
    category          varchar(30)    NOT NULL,
    description       varchar(300)   NOT NULL,
    estimated_value   numeric(19, 4) NOT NULL,
    forced_sale_value numeric(19, 4) NOT NULL,
    valuation_date    date           NOT NULL,
    status            varchar(10)    NOT NULL,
    verified_by       uuid,
    verified_at       timestamptz,
    released_at       timestamptz,
    created_at        timestamptz    NOT NULL,
    CONSTRAINT pk_loan_collateral PRIMARY KEY (id),
    CONSTRAINT fk_loan_collateral_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES core.loan_application (tenant_id, id),
    CONSTRAINT ck_loan_collateral_category CHECK (category IN ('LAND', 'BUILDING', 'VEHICLE', 'EQUIPMENT',
        'INVENTORY', 'SAVINGS', 'HOUSEHOLD', 'OTHER')),
    CONSTRAINT ck_loan_collateral_values CHECK (estimated_value > 0 AND forced_sale_value > 0
        AND forced_sale_value <= estimated_value),
    CONSTRAINT ck_loan_collateral_status CHECK (status IN ('PLEDGED', 'RELEASED')
        AND (status = 'RELEASED') = (released_at IS NOT NULL)),
    CONSTRAINT ck_loan_collateral_verified CHECK ((verified_by IS NULL) = (verified_at IS NULL))
);

SELECT core.apply_tenant_isolation('core.loan_collateral');
GRANT SELECT, INSERT ON core.loan_collateral TO ${app_db_role};
GRANT UPDATE (status, verified_by, verified_at, released_at) ON core.loan_collateral TO ${app_db_role};

CREATE TABLE core.loan
(
    id                         uuid           NOT NULL,
    tenant_id                  uuid           NOT NULL,
    loan_number                varchar(30)    NOT NULL,
    application_id             uuid           NOT NULL,
    customer_id                uuid           NOT NULL,
    branch_id                  uuid           NOT NULL,
    product_version_id         uuid           NOT NULL,
    repayment_account_id       uuid           NOT NULL,
    currency                   varchar(3)     NOT NULL,
    principal                  numeric(19, 4) NOT NULL,
    interest_method            varchar(40)    NOT NULL,
    annual_rate                numeric(9, 6)  NOT NULL,
    day_count                  varchar(12)    NOT NULL,
    repayment_frequency        varchar(10)    NOT NULL,
    installments               integer        NOT NULL,
    principal_grace            integer        NOT NULL,
    interest_grace             integer        NOT NULL,
    rounding_mode              varchar(10)    NOT NULL,
    allocation_order           varchar(40)    NOT NULL,
    penalty_rate               numeric(9, 6)  NOT NULL,
    penalty_grace_days         integer        NOT NULL,
    processing_fee             numeric(19, 4) NOT NULL,
    principal_ledger_account_id uuid          NOT NULL,
    interest_ledger_account_id uuid           NOT NULL,
    penalty_ledger_account_id  uuid           NOT NULL,
    interest_income_gl_id      uuid           NOT NULL,
    penalty_income_gl_id       uuid           NOT NULL,
    fee_income_gl_id           uuid           NOT NULL,
    disbursement_date          date           NOT NULL,
    first_due_date             date           NOT NULL,
    maturity_date              date           NOT NULL,
    status                     varchar(12)    NOT NULL,
    schedule_version           integer        NOT NULL DEFAULT 1,
    days_past_due              integer        NOT NULL DEFAULT 0,
    delinquency_band           varchar(20),
    non_accrual                boolean        NOT NULL DEFAULT false,
    interest_recognised        numeric(19, 4) NOT NULL DEFAULT 0,
    interest_accrued_through   date,
    disbursement_transaction_id uuid          NOT NULL,
    disbursed_by               uuid           NOT NULL,
    disbursed_at               timestamptz    NOT NULL,
    closed_on                  date,
    version                    bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_loan PRIMARY KEY (id),
    CONSTRAINT uq_loan_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_loan_number UNIQUE (tenant_id, loan_number),
    CONSTRAINT uq_loan_application UNIQUE (tenant_id, application_id),
    CONSTRAINT fk_loan_application FOREIGN KEY (tenant_id, application_id)
        REFERENCES core.loan_application (tenant_id, id),
    CONSTRAINT fk_loan_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_loan_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_loan_product_version FOREIGN KEY (tenant_id, product_version_id)
        REFERENCES core.loan_product_version (tenant_id, id),
    CONSTRAINT fk_loan_repayment_account FOREIGN KEY (tenant_id, repayment_account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_loan_principal_account FOREIGN KEY (tenant_id, principal_ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT fk_loan_interest_account FOREIGN KEY (tenant_id, interest_ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT fk_loan_penalty_account FOREIGN KEY (tenant_id, penalty_ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT fk_loan_disbursement FOREIGN KEY (tenant_id, disbursement_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id),
    CONSTRAINT ck_loan_status CHECK (status IN ('ACTIVE', 'CLOSED', 'WRITTEN_OFF')
        AND (status = 'ACTIVE') = (closed_on IS NULL)),
    CONSTRAINT ck_loan_amounts CHECK (principal > 0 AND processing_fee >= 0 AND interest_recognised >= 0
        AND days_past_due >= 0 AND schedule_version >= 1),
    CONSTRAINT ck_loan_dates CHECK (first_due_date > disbursement_date AND maturity_date >= first_due_date)
);

CREATE INDEX ix_loan_customer ON core.loan (tenant_id, customer_id);
CREATE INDEX ix_loan_status ON core.loan (tenant_id, status, branch_id);
SELECT core.apply_tenant_isolation('core.loan');
CREATE TRIGGER trg_loan_no_delete
    BEFORE DELETE ON core.loan
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan TO ${app_db_role};
GRANT UPDATE (status, schedule_version, days_past_due, delinquency_band, non_accrual, interest_recognised,
    interest_accrued_through, closed_on, version) ON core.loan TO ${app_db_role};

-- The contractual plan (versioned: a restructure adds a version) and what repayments settled of each installment.
CREATE TABLE core.loan_installment
(
    loan_id          uuid           NOT NULL,
    schedule_version integer        NOT NULL,
    number           integer        NOT NULL,
    tenant_id        uuid           NOT NULL,
    from_date        date           NOT NULL,
    due_date         date           NOT NULL,
    principal_due    numeric(19, 4) NOT NULL,
    interest_due     numeric(19, 4) NOT NULL,
    penalty_due      numeric(19, 4) NOT NULL DEFAULT 0,
    principal_paid   numeric(19, 4) NOT NULL DEFAULT 0,
    interest_paid    numeric(19, 4) NOT NULL DEFAULT 0,
    penalty_paid     numeric(19, 4) NOT NULL DEFAULT 0,
    interest_waived  numeric(19, 4) NOT NULL DEFAULT 0,
    paid_on          date,
    CONSTRAINT pk_loan_installment PRIMARY KEY (loan_id, schedule_version, number),
    CONSTRAINT fk_loan_installment_loan FOREIGN KEY (tenant_id, loan_id) REFERENCES core.loan (tenant_id, id),
    CONSTRAINT ck_loan_installment_amounts CHECK (principal_due >= 0 AND interest_due >= 0 AND penalty_due >= 0
        AND principal_paid BETWEEN 0 AND principal_due
        AND interest_paid >= 0 AND interest_paid + interest_waived <= interest_due AND interest_waived >= 0
        AND penalty_paid BETWEEN 0 AND penalty_due),
    CONSTRAINT ck_loan_installment_dates CHECK (due_date > from_date)
);

CREATE INDEX ix_loan_installment_due ON core.loan_installment (tenant_id, due_date);
SELECT core.apply_tenant_isolation('core.loan_installment');
CREATE TRIGGER trg_loan_installment_no_delete
    BEFORE DELETE ON core.loan_installment
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan_installment TO ${app_db_role};
GRANT UPDATE (penalty_due, principal_paid, interest_paid, penalty_paid, interest_waived, paid_on)
    ON core.loan_installment TO ${app_db_role};

CREATE TABLE core.loan_repayment
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    loan_id                  uuid           NOT NULL,
    financial_transaction_id uuid           NOT NULL,
    source                   varchar(10)    NOT NULL,
    amount                   numeric(19, 4) NOT NULL,
    penalty_allocated        numeric(19, 4) NOT NULL,
    fee_allocated            numeric(19, 4) NOT NULL,
    interest_allocated       numeric(19, 4) NOT NULL,
    principal_allocated      numeric(19, 4) NOT NULL,
    business_date            date           NOT NULL,
    received_by              uuid,
    created_at               timestamptz    NOT NULL,
    CONSTRAINT pk_loan_repayment PRIMARY KEY (id),
    CONSTRAINT uq_loan_repayment_transaction UNIQUE (tenant_id, financial_transaction_id),
    CONSTRAINT fk_loan_repayment_loan FOREIGN KEY (tenant_id, loan_id) REFERENCES core.loan (tenant_id, id),
    CONSTRAINT fk_loan_repayment_transaction FOREIGN KEY (tenant_id, financial_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id),
    CONSTRAINT ck_loan_repayment_source CHECK (source IN ('ACCOUNT', 'CASH', 'FIELD')),
    CONSTRAINT ck_loan_repayment_split CHECK (amount > 0 AND penalty_allocated >= 0 AND fee_allocated >= 0
        AND interest_allocated >= 0 AND principal_allocated >= 0
        AND amount = penalty_allocated + fee_allocated + interest_allocated + principal_allocated)
);

CREATE INDEX ix_loan_repayment_loan ON core.loan_repayment (tenant_id, loan_id, created_at);
SELECT core.apply_tenant_isolation('core.loan_repayment');
CREATE TRIGGER trg_loan_repayment_immutable
    BEFORE UPDATE OR DELETE ON core.loan_repayment
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.loan_repayment TO ${app_db_role};
