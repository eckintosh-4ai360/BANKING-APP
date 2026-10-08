-- =====================================================================================================
-- V16 Product charges and financial transactions.
--
-- A charge belongs to a product version, so it is part of the terms an account was opened under and can only be
-- edited while the version is a draft. A financial transaction is the business record of one money movement
-- (deposit, withdrawal, transfer); its money lives in the journal it points to. Transactions are never deleted
-- and only change when they are reversed.
-- =====================================================================================================

CREATE TABLE core.product_charge
(
    id                 uuid           NOT NULL,
    tenant_id          uuid           NOT NULL,
    product_version_id uuid           NOT NULL,
    charge_event       varchar(20)    NOT NULL,
    name               varchar(80)    NOT NULL,
    calculation        varchar(10)    NOT NULL,
    flat_amount        numeric(19, 4),
    rate               numeric(9, 6),
    min_amount         numeric(19, 4),
    max_amount         numeric(19, 4),
    CONSTRAINT pk_product_charge PRIMARY KEY (id),
    CONSTRAINT uq_product_charge_event UNIQUE (tenant_id, product_version_id, charge_event),
    CONSTRAINT fk_product_charge_version FOREIGN KEY (tenant_id, product_version_id)
        REFERENCES core.account_product_version (tenant_id, id),
    CONSTRAINT ck_product_charge_event CHECK (charge_event IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'TRANSFER_OUT')),
    CONSTRAINT ck_product_charge_calculation CHECK (
        (calculation = 'FLAT' AND flat_amount > 0 AND rate IS NULL)
            OR (calculation = 'PERCENT' AND rate > 0 AND rate <= 100 AND flat_amount IS NULL)),
    CONSTRAINT ck_product_charge_bounds CHECK (
        (min_amount IS NULL OR min_amount >= 0) AND (max_amount IS NULL OR max_amount > 0)
            AND (min_amount IS NULL OR max_amount IS NULL OR min_amount <= max_amount))
);

SELECT core.apply_tenant_isolation('core.product_charge');

-- Charges are terms: they change only while their version is a draft.
CREATE FUNCTION core.product_charge_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    v_status varchar(10);
BEGIN
    SELECT status INTO v_status
    FROM core.account_product_version
    WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.product_version_id ELSE NEW.product_version_id END;
    IF v_status IS DISTINCT FROM 'DRAFT' THEN
        RAISE EXCEPTION 'Charges of a published product version cannot change' USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'UPDATE' AND NEW.product_version_id <> OLD.product_version_id THEN
        RAISE EXCEPTION 'A charge cannot move to another product version' USING ERRCODE = 'check_violation';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$;

CREATE TRIGGER trg_product_charge_guard
    BEFORE INSERT OR UPDATE OR DELETE ON core.product_charge
    FOR EACH ROW EXECUTE FUNCTION core.product_charge_guard();

GRANT SELECT, INSERT, UPDATE, DELETE ON core.product_charge TO ${app_db_role};

-- ------------------------------------------------------------------------------------- financial_transaction

CREATE TABLE core.financial_transaction
(
    id                        uuid           NOT NULL,
    tenant_id                 uuid           NOT NULL,
    reference                 varchar(40)    NOT NULL,
    transaction_type          varchar(20)    NOT NULL,
    status                    varchar(10)    NOT NULL,
    channel                   varchar(10)    NOT NULL,
    currency                  varchar(3)     NOT NULL,
    amount                    numeric(19, 4) NOT NULL,
    fee_amount                numeric(19, 4) NOT NULL DEFAULT 0,
    debit_account_id          uuid,
    credit_account_id         uuid,
    branch_id                 uuid           NOT NULL,
    journal_entry_id          uuid           NOT NULL,
    business_date             date           NOT NULL,
    value_date                date           NOT NULL,
    narration                 varchar(200),
    external_reference        varchar(60),
    idempotency_key           varchar(100),
    initiated_by              uuid,
    created_at                timestamptz    NOT NULL,
    reversal_journal_entry_id uuid,
    reversed_at               timestamptz,
    reversed_by               uuid,
    reversal_reason           varchar(300),
    CONSTRAINT pk_financial_transaction PRIMARY KEY (id),
    CONSTRAINT uq_financial_transaction_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_financial_transaction_reference UNIQUE (tenant_id, reference),
    CONSTRAINT uq_financial_transaction_journal UNIQUE (tenant_id, journal_entry_id),
    CONSTRAINT fk_financial_transaction_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_financial_transaction_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_financial_transaction_debit FOREIGN KEY (tenant_id, debit_account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_financial_transaction_credit FOREIGN KEY (tenant_id, credit_account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_financial_transaction_branch FOREIGN KEY (tenant_id, branch_id)
        REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_financial_transaction_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT fk_financial_transaction_reversal_journal FOREIGN KEY (tenant_id, reversal_journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_financial_transaction_type
        CHECK (transaction_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'TRANSFER')),
    CONSTRAINT ck_financial_transaction_status CHECK (status IN ('POSTED', 'REVERSED')),
    CONSTRAINT ck_financial_transaction_channel CHECK (channel IN ('BRANCH', 'MOBILE', 'FIELD', 'SYSTEM')),
    CONSTRAINT ck_financial_transaction_amounts CHECK (amount > 0 AND fee_amount >= 0),
    CONSTRAINT ck_financial_transaction_accounts CHECK (
        (transaction_type = 'CASH_DEPOSIT' AND credit_account_id IS NOT NULL AND debit_account_id IS NULL)
            OR (transaction_type = 'CASH_WITHDRAWAL' AND debit_account_id IS NOT NULL AND credit_account_id IS NULL)
            OR (transaction_type = 'TRANSFER' AND debit_account_id IS NOT NULL AND credit_account_id IS NOT NULL
            AND debit_account_id <> credit_account_id)),
    CONSTRAINT ck_financial_transaction_reversed CHECK (
        (status = 'REVERSED') = (reversal_journal_entry_id IS NOT NULL)
            AND (status = 'REVERSED') = (reversed_at IS NOT NULL))
);

CREATE INDEX ix_financial_transaction_debit ON core.financial_transaction (tenant_id, debit_account_id, business_date)
    WHERE debit_account_id IS NOT NULL;
CREATE INDEX ix_financial_transaction_credit ON core.financial_transaction (tenant_id, credit_account_id, business_date)
    WHERE credit_account_id IS NOT NULL;
CREATE INDEX ix_financial_transaction_branch_date ON core.financial_transaction (tenant_id, branch_id, business_date);
SELECT core.apply_tenant_isolation('core.financial_transaction');

-- Posted transactions never change (spec rules 6 and 7). Reversal is the only transition, and it only adds the
-- reversal details.
CREATE FUNCTION core.financial_transaction_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.status <> 'POSTED' OR NEW.status <> 'REVERSED'
        OR (to_jsonb(NEW) - ARRAY ['status', 'reversal_journal_entry_id', 'reversed_at', 'reversed_by',
            'reversal_reason'])
           <> (to_jsonb(OLD) - ARRAY ['status', 'reversal_journal_entry_id', 'reversed_at', 'reversed_by',
            'reversal_reason']) THEN
        RAISE EXCEPTION 'A financial transaction can only be reversed, once' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_financial_transaction_guard
    BEFORE UPDATE ON core.financial_transaction
    FOR EACH ROW EXECUTE FUNCTION core.financial_transaction_guard();
CREATE TRIGGER trg_financial_transaction_no_delete
    BEFORE DELETE ON core.financial_transaction
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
CREATE TRIGGER trg_financial_transaction_no_truncate
    BEFORE TRUNCATE ON core.financial_transaction
    FOR EACH STATEMENT EXECUTE FUNCTION core.reject_mutation();

GRANT SELECT, INSERT ON core.financial_transaction TO ${app_db_role};
GRANT UPDATE (status, reversal_journal_entry_id, reversed_at, reversed_by, reversal_reason)
    ON core.financial_transaction TO ${app_db_role};

-- A journal that names a transaction must belong to one that exists. Checked at commit, because the transaction
-- row is written after its journal in the same database transaction.
ALTER TABLE core.journal_entry
    ADD CONSTRAINT fk_journal_entry_financial_transaction FOREIGN KEY (tenant_id, financial_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id) DEFERRABLE INITIALLY DEFERRED;
