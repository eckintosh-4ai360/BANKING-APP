-- =====================================================================================================
-- V21 Deposit interest (daily accruals, the running position per account, payouts) and the business date of
-- each account's last customer activity, for dormancy.
--
-- End-of-day accrues each day's interest exactly (8 decimals) on the closing balance and posts the change of the
-- rounded cumulative total to the GL, so an account's share of interest payable is always round(accrued) - paid.
-- At the end of each interest period the account is paid round(accrued to the period end) - paid, so sub-cent
-- remainders carry forward and nothing is rounded twice.
-- =====================================================================================================

CREATE TABLE core.deposit_interest_position
(
    account_id        uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    accrued_exact     numeric(19, 8) NOT NULL DEFAULT 0,
    settled_exact     numeric(19, 8) NOT NULL DEFAULT 0,
    paid_out          numeric(19, 4) NOT NULL DEFAULT 0,
    period_start      date           NOT NULL,
    last_accrual_date date,
    updated_at        timestamptz    NOT NULL,
    CONSTRAINT pk_deposit_interest_position PRIMARY KEY (account_id),
    CONSTRAINT fk_deposit_interest_position_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT ck_deposit_interest_position_totals
        CHECK (settled_exact >= 0 AND accrued_exact >= settled_exact AND paid_out >= 0)
);

COMMENT ON COLUMN core.deposit_interest_position.accrued_exact IS
    'Exact interest accrued since the account first earned interest (daily-balance methods); never decreases';
COMMENT ON COLUMN core.deposit_interest_position.settled_exact IS 'accrued_exact as of the last period end paid';
COMMENT ON COLUMN core.deposit_interest_position.paid_out IS 'Interest paid from interest payable, in minor units';

SELECT core.apply_tenant_isolation('core.deposit_interest_position');
CREATE TRIGGER trg_deposit_interest_position_no_delete
    BEFORE DELETE ON core.deposit_interest_position
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.deposit_interest_position TO ${app_db_role};
GRANT UPDATE (accrued_exact, settled_exact, paid_out, period_start, last_accrual_date, updated_at)
    ON core.deposit_interest_position TO ${app_db_role};

-- One row per account and calendar day accrued (weekend and holiday days are accrued by the preceding business
-- date's end-of-day, on that date's closing balance).
CREATE TABLE core.deposit_interest_accrual
(
    account_id       uuid           NOT NULL,
    accrual_date     date           NOT NULL,
    tenant_id        uuid           NOT NULL,
    business_date    date           NOT NULL,
    branch_id        uuid           NOT NULL,
    expense_gl_id    uuid           NOT NULL,
    currency         varchar(3)     NOT NULL,
    balance          numeric(19, 4) NOT NULL,
    rate             numeric(9, 6)  NOT NULL,
    day_weight       integer        NOT NULL,
    amount           numeric(19, 8) NOT NULL,
    gl_amount        numeric(19, 4) NOT NULL,
    journal_entry_id uuid,
    CONSTRAINT pk_deposit_interest_accrual PRIMARY KEY (account_id, accrual_date),
    CONSTRAINT fk_deposit_interest_accrual_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_deposit_interest_accrual_gl FOREIGN KEY (tenant_id, expense_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_deposit_interest_accrual_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_deposit_interest_accrual_amounts CHECK (amount >= 0 AND gl_amount >= 0 AND day_weight >= 0)
);

-- Only accruals that move the GL are posted; the rest (minimum-balance products, sub-cent days) never get a journal.
CREATE INDEX ix_deposit_interest_accrual_unposted ON core.deposit_interest_accrual (tenant_id, business_date)
    WHERE journal_entry_id IS NULL AND gl_amount > 0;
SELECT core.apply_tenant_isolation('core.deposit_interest_accrual');

-- An accrual never changes, except to record (once) the GL journal it was posted in.
CREATE FUNCTION core.deposit_interest_accrual_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.journal_entry_id IS NOT NULL
        OR (to_jsonb(NEW) - 'journal_entry_id') <> (to_jsonb(OLD) - 'journal_entry_id') THEN
        RAISE EXCEPTION 'An interest accrual cannot change' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_deposit_interest_accrual_guard
    BEFORE UPDATE ON core.deposit_interest_accrual
    FOR EACH ROW EXECUTE FUNCTION core.deposit_interest_accrual_guard();
CREATE TRIGGER trg_deposit_interest_accrual_no_delete
    BEFORE DELETE ON core.deposit_interest_accrual
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.deposit_interest_accrual TO ${app_db_role};
GRANT UPDATE (journal_entry_id) ON core.deposit_interest_accrual TO ${app_db_role};

CREATE TABLE core.deposit_interest_payout
(
    account_id       uuid           NOT NULL,
    period_end       date           NOT NULL,
    tenant_id        uuid           NOT NULL,
    period_start     date           NOT NULL,
    amount           numeric(19, 4) NOT NULL,
    exact_amount     numeric(19, 8) NOT NULL,
    journal_entry_id uuid,
    created_at       timestamptz    NOT NULL,
    CONSTRAINT pk_deposit_interest_payout PRIMARY KEY (account_id, period_end),
    CONSTRAINT fk_deposit_interest_payout_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_deposit_interest_payout_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_deposit_interest_payout_amount CHECK (amount >= 0),
    CONSTRAINT ck_deposit_interest_payout_journal CHECK ((amount > 0) = (journal_entry_id IS NOT NULL))
);

SELECT core.apply_tenant_isolation('core.deposit_interest_payout');
CREATE TRIGGER trg_deposit_interest_payout_immutable
    BEFORE UPDATE OR DELETE ON core.deposit_interest_payout
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.deposit_interest_payout TO ${app_db_role};

-- Dormancy counts business days, so an account remembers the business date of its last customer posting (the
-- instant alone cannot say which business date it belonged to). Existing accounts start from the calendar date of
-- their last activity in the institution's time zone.
ALTER TABLE core.account ADD COLUMN last_activity_on date;

UPDATE core.account a
SET last_activity_on = (a.last_activity_at AT TIME ZONE t.timezone)::date
FROM core.tenant t
WHERE t.id = a.tenant_id AND a.last_activity_at IS NOT NULL;

GRANT UPDATE (last_activity_on) ON core.account TO ${app_db_role};
