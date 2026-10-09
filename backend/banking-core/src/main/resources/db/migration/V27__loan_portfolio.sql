-- =====================================================================================================
-- V27 Loan portfolio: delinquency bands (institution configuration), penalty accrual, non-accrual and
-- provisioning state kept by end-of-day.
--
-- A loan's band follows its days past due: each band from its min_days up to the next band's. The band sets the
-- provision held against the principal outstanding and whether interest stops being recognised as income (it is
-- then held in suspense until collected). The defaults an institution starts with are a starting point to review
-- against its regulator's rules, not a statement of any regulation.
-- =====================================================================================================

CREATE TABLE core.loan_delinquency_band
(
    tenant_id       uuid          NOT NULL,
    code            varchar(20)   NOT NULL,
    name            varchar(60)   NOT NULL,
    min_days        integer       NOT NULL,
    provision_rate  numeric(9, 4) NOT NULL,
    suspend_accrual boolean       NOT NULL,
    updated_at      timestamptz   NOT NULL,
    CONSTRAINT pk_loan_delinquency_band PRIMARY KEY (tenant_id, code),
    CONSTRAINT uq_loan_delinquency_band_min_days UNIQUE (tenant_id, min_days),
    CONSTRAINT fk_loan_delinquency_band_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_loan_delinquency_band_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
    CONSTRAINT ck_loan_delinquency_band_values CHECK (min_days BETWEEN 0 AND 3650
        AND provision_rate BETWEEN 0 AND 100)
);

SELECT core.apply_tenant_isolation('core.loan_delinquency_band');
GRANT SELECT, INSERT, DELETE ON core.loan_delinquency_band TO ${app_db_role};
GRANT UPDATE (name, min_days, provision_rate, suspend_accrual, updated_at) ON core.loan_delinquency_band
    TO ${app_db_role};

-- provision_held: the allowance held for the loan (credit 1290); portfolio_processed_through: the last business date
-- end-of-day classified the loan (a resumed run skips it); penalty_accrued_through: the last day penalties ran for.
ALTER TABLE core.loan
    ADD COLUMN provision_held              numeric(19, 4) NOT NULL DEFAULT 0,
    ADD COLUMN penalty_accrued_through     date,
    ADD COLUMN portfolio_processed_through date,
    ADD CONSTRAINT ck_loan_provision CHECK (provision_held >= 0);
GRANT UPDATE (provision_held, penalty_accrued_through, portfolio_processed_through) ON core.loan TO ${app_db_role};

-- Penalties accrue exactly and are rounded cumulatively (penalty_due = the rounded exact total), so they never drift.
ALTER TABLE core.loan_installment
    ADD COLUMN penalty_exact numeric(28, 10) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_loan_installment_penalty_exact CHECK (penalty_exact >= 0);
GRANT UPDATE (penalty_exact) ON core.loan_installment TO ${app_db_role};

CREATE INDEX ix_loan_active_band ON core.loan (tenant_id, delinquency_band) WHERE status = 'ACTIVE';
