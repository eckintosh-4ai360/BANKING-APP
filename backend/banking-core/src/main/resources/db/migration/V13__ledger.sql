-- =====================================================================================================
-- V13 Ledger: the financial source of truth.
--
-- Invariants enforced by the database, not only by the application (the Posting Engine checks first):
--   1. Every journal balances per currency and per branch (deferred check at commit).
--   2. Journals and their lines are append-only; lines can only be added in the journal's own transaction.
--   3. Lines post only to active, non-header GL accounts, and a sub-ledger line matches its account's GL,
--      branch and currency.
--   4. Lines post only into an OPEN accounting period; closing a period waits for in-flight postings.
--   5. A reversal mirrors its original exactly, and a journal is reversed at most once.
--   6. account_balance is maintained only by a trigger from ledger_entry (the application cannot write the
--      balance), and balance-checked accounts can never go below zero plus their overdraft limit.
-- =====================================================================================================

-- --------------------------------------------------------------------------------------------- currency

CREATE TABLE core.currency
(
    code        varchar(3)  NOT NULL,
    name        varchar(60) NOT NULL,
    minor_units smallint    NOT NULL,
    active      boolean     NOT NULL DEFAULT true,
    CONSTRAINT pk_currency PRIMARY KEY (code),
    CONSTRAINT ck_currency_code CHECK (code ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_currency_minor_units CHECK (minor_units BETWEEN 0 AND 4)
);

INSERT INTO core.currency (code, name, minor_units)
VALUES ('GHS', 'Ghana cedi', 2),
       ('NGN', 'Nigerian naira', 2),
       ('KES', 'Kenyan shilling', 2),
       ('ZAR', 'South African rand', 2),
       ('USD', 'US dollar', 2),
       ('EUR', 'Euro', 2),
       ('GBP', 'Pound sterling', 2),
       ('XOF', 'West African CFA franc', 0),
       ('XAF', 'Central African CFA franc', 0),
       ('UGX', 'Ugandan shilling', 0),
       ('RWF', 'Rwandan franc', 0);

-- Reference data shared by all tenants: read-only for the application.
GRANT SELECT ON core.currency TO ${app_db_role};

-- ------------------------------------------------------------------------------------ accounting_period

CREATE TABLE core.accounting_period
(
    tenant_id    uuid        NOT NULL,
    period_start date        NOT NULL,
    period_end   date        NOT NULL,
    status       varchar(10) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    closed_at    timestamptz,
    closed_by    uuid,
    CONSTRAINT pk_accounting_period PRIMARY KEY (tenant_id, period_start),
    CONSTRAINT fk_accounting_period_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_accounting_period_status CHECK (status IN ('OPEN', 'CLOSING', 'CLOSED')),
    -- Calendar months only, so periods can never overlap or leave gaps.
    CONSTRAINT ck_accounting_period_month CHECK (
        period_start = date_trunc('month', period_start)::date
            AND period_end = (date_trunc('month', period_start) + interval '1 month - 1 day')::date),
    CONSTRAINT ck_accounting_period_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL))
);

SELECT core.apply_tenant_isolation('core.accounting_period');

CREATE FUNCTION core.accounting_period_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'Accounting period % is closed and cannot be reopened', OLD.period_start
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_accounting_period_guard
    BEFORE UPDATE ON core.accounting_period
    FOR EACH ROW EXECUTE FUNCTION core.accounting_period_guard();
CREATE TRIGGER trg_accounting_period_no_delete
    BEFORE DELETE ON core.accounting_period
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();

GRANT SELECT, INSERT ON core.accounting_period TO ${app_db_role};
GRANT UPDATE (status, closed_at, closed_by) ON core.accounting_period TO ${app_db_role};

-- -------------------------------------------------------------------------------------- chart_of_account

CREATE TABLE core.chart_of_account
(
    id                     uuid         NOT NULL,
    tenant_id              uuid         NOT NULL,
    code                   varchar(20)  NOT NULL,
    name                   varchar(120) NOT NULL,
    account_class          varchar(10)  NOT NULL,
    normal_side            varchar(6)   NOT NULL,
    parent_id              uuid,
    is_header              boolean      NOT NULL,
    manual_posting_allowed boolean      NOT NULL DEFAULT false,
    system_code            varchar(40),
    status                 varchar(10)  NOT NULL,
    created_at             timestamptz  NOT NULL,
    updated_at             timestamptz  NOT NULL,
    created_by             uuid,
    updated_by             uuid,
    version                bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_chart_of_account PRIMARY KEY (id),
    CONSTRAINT uq_chart_of_account_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_chart_of_account_code UNIQUE (tenant_id, code),
    CONSTRAINT uq_chart_of_account_system_code UNIQUE (tenant_id, system_code),
    CONSTRAINT fk_chart_of_account_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_chart_of_account_parent FOREIGN KEY (tenant_id, parent_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT ck_chart_of_account_code CHECK (code ~ '^[0-9A-Z][0-9A-Z.-]{0,19}$'),
    CONSTRAINT ck_chart_of_account_class
        CHECK (account_class IN ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
    CONSTRAINT ck_chart_of_account_normal_side CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_chart_of_account_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT ck_chart_of_account_parent CHECK (parent_id IS NULL OR parent_id <> id),
    CONSTRAINT ck_chart_of_account_header_manual CHECK (NOT (is_header AND manual_posting_allowed))
);

CREATE INDEX ix_chart_of_account_parent ON core.chart_of_account (tenant_id, parent_id);
SELECT core.apply_tenant_isolation('core.chart_of_account');

-- A parent must be a header account of the same class, so roll-ups (trial balance, statements) stay consistent.
CREATE FUNCTION core.chart_of_account_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_parent core.chart_of_account%ROWTYPE;
BEGIN
    IF NEW.parent_id IS NOT NULL THEN
        SELECT * INTO v_parent FROM core.chart_of_account WHERE id = NEW.parent_id AND tenant_id = NEW.tenant_id;
        IF NOT v_parent.is_header OR v_parent.account_class <> NEW.account_class THEN
            RAISE EXCEPTION 'The parent must be a header account of the same class'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_chart_of_account_before_insert
    BEFORE INSERT ON core.chart_of_account
    FOR EACH ROW EXECUTE FUNCTION core.chart_of_account_before_insert();
CREATE TRIGGER trg_chart_of_account_no_delete
    BEFORE DELETE ON core.chart_of_account
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();

-- Structure (class, side, header, system code, parent) is fixed once created: postings depend on it.
GRANT SELECT, INSERT ON core.chart_of_account TO ${app_db_role};
GRANT UPDATE (name, status, manual_posting_allowed, updated_at, updated_by, version)
    ON core.chart_of_account TO ${app_db_role};

-- ---------------------------------------------------------------------------------------- ledger_account

-- Sub-ledger accounts: a customer account, teller drawer, collector's cash, settlement account... Each one rolls
-- up into exactly one GL account and lives in one branch and one currency.
CREATE TABLE core.ledger_account
(
    id                  uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    chart_of_account_id uuid         NOT NULL,
    branch_id           uuid         NOT NULL,
    currency            varchar(3)   NOT NULL,
    ledger_account_type varchar(20)  NOT NULL,
    normal_side         varchar(6)   NOT NULL,
    balance_check       boolean      NOT NULL,
    name                varchar(120) NOT NULL,
    owner_type          varchar(20),
    owner_id            uuid,
    status              varchar(10)  NOT NULL,
    created_at          timestamptz  NOT NULL,
    created_by          uuid,
    closed_at           timestamptz,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_account PRIMARY KEY (id),
    CONSTRAINT uq_ledger_account_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_ledger_account_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_ledger_account_gl FOREIGN KEY (tenant_id, chart_of_account_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_ledger_account_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_ledger_account_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT ck_ledger_account_type CHECK (ledger_account_type IN
                                             ('CUSTOMER_DEPOSIT', 'LOAN', 'TELLER_DRAWER', 'VAULT',
                                              'COLLECTOR_CASH', 'SETTLEMENT', 'SUSPENSE', 'CASH_IN_TRANSIT',
                                              'INTERNAL')),
    CONSTRAINT ck_ledger_account_normal_side CHECK (normal_side IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_ledger_account_status CHECK (status IN ('ACTIVE', 'CLOSED')),
    CONSTRAINT ck_ledger_account_closed CHECK ((status = 'CLOSED') = (closed_at IS NOT NULL)),
    CONSTRAINT ck_ledger_account_owner CHECK ((owner_type IS NULL) = (owner_id IS NULL))
);

CREATE UNIQUE INDEX uq_ledger_account_owner ON core.ledger_account (tenant_id, owner_type, owner_id)
    WHERE owner_id IS NOT NULL;
CREATE INDEX ix_ledger_account_gl ON core.ledger_account (tenant_id, chart_of_account_id);
SELECT core.apply_tenant_isolation('core.ledger_account');

GRANT SELECT, INSERT ON core.ledger_account TO ${app_db_role};
GRANT UPDATE (name, status, closed_at, version) ON core.ledger_account TO ${app_db_role};

-- -------------------------------------------------------------------------------------- account_balance

CREATE TABLE core.account_balance
(
    ledger_account_id     uuid          NOT NULL,
    tenant_id             uuid          NOT NULL,
    currency              varchar(3)    NOT NULL,
    normal_side           varchar(6)    NOT NULL,
    balance_check         boolean       NOT NULL,
    ledger_balance        numeric(19, 4) NOT NULL DEFAULT 0,
    hold_amount           numeric(19, 4) NOT NULL DEFAULT 0,
    overdraft_limit       numeric(19, 4) NOT NULL DEFAULT 0,
    available_balance     numeric(19, 4) GENERATED ALWAYS AS (ledger_balance - hold_amount) STORED,
    last_journal_entry_id uuid,
    last_posted_at        timestamptz,
    version               bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_balance PRIMARY KEY (ledger_account_id),
    CONSTRAINT uq_account_balance_tenant UNIQUE (tenant_id, ledger_account_id),
    CONSTRAINT fk_account_balance_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT ck_account_balance_hold CHECK (hold_amount >= 0),
    CONSTRAINT ck_account_balance_overdraft_limit CHECK (overdraft_limit >= 0),
    -- The database itself refuses an overdraft on balance-checked accounts.
    CONSTRAINT ck_account_balance_no_overdraft
        CHECK (NOT balance_check OR ledger_balance - hold_amount + overdraft_limit >= 0)
);

SELECT core.apply_tenant_isolation('core.account_balance');

-- The balance is written only by the ledger triggers below. The application may lock rows (FOR UPDATE needs an
-- UPDATE grant on some column) and set the overdraft limit, nothing else.
GRANT SELECT ON core.account_balance TO ${app_db_role};
GRANT UPDATE (overdraft_limit) ON core.account_balance TO ${app_db_role};

-- ----------------------------------------------------------------------------- journal_entry / ledger_entry

CREATE TABLE core.journal_entry
(
    id                       uuid         NOT NULL,
    tenant_id                uuid         NOT NULL,
    journal_number           varchar(30)  NOT NULL,
    business_date            date         NOT NULL,
    value_date               date         NOT NULL,
    posted_at                timestamptz  NOT NULL DEFAULT now(),
    source_type              varchar(20)  NOT NULL,
    source_reference         varchar(60),
    financial_transaction_id uuid,
    reverses_journal_id      uuid,
    branch_id                uuid         NOT NULL,
    posted_by                uuid,
    approved_by              uuid,
    description              varchar(300) NOT NULL,
    posting_txid             xid8         NOT NULL DEFAULT pg_current_xact_id(),
    CONSTRAINT pk_journal_entry PRIMARY KEY (id),
    CONSTRAINT uq_journal_entry_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_journal_entry_number UNIQUE (tenant_id, journal_number),
    CONSTRAINT uq_journal_entry_reverses UNIQUE (tenant_id, reverses_journal_id),
    CONSTRAINT fk_journal_entry_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_journal_entry_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_journal_entry_reverses FOREIGN KEY (tenant_id, reverses_journal_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_journal_entry_source CHECK (source_type IN
                                              ('TRANSACTION', 'MANUAL', 'REVERSAL', 'EOD', 'LOAN', 'PROVISION',
                                               'PERIOD_CLOSE', 'OPENING_BALANCE')),
    CONSTRAINT ck_journal_entry_reversal CHECK ((reverses_journal_id IS NULL) = (source_type <> 'REVERSAL')),
    CONSTRAINT ck_journal_entry_not_self CHECK (reverses_journal_id IS NULL OR reverses_journal_id <> id),
    CONSTRAINT ck_journal_entry_four_eyes CHECK (approved_by IS NULL OR approved_by IS DISTINCT FROM posted_by)
);

CREATE INDEX ix_journal_entry_date ON core.journal_entry (tenant_id, business_date, id);
CREATE INDEX ix_journal_entry_transaction ON core.journal_entry (tenant_id, financial_transaction_id)
    WHERE financial_transaction_id IS NOT NULL;
CREATE INDEX ix_journal_entry_source_reference ON core.journal_entry (tenant_id, source_reference)
    WHERE source_reference IS NOT NULL;
SELECT core.apply_tenant_isolation('core.journal_entry');

CREATE TABLE core.ledger_entry
(
    id                  uuid           NOT NULL,
    tenant_id           uuid           NOT NULL,
    journal_entry_id    uuid           NOT NULL,
    line_no             smallint       NOT NULL,
    chart_of_account_id uuid           NOT NULL,
    ledger_account_id   uuid,
    branch_id           uuid           NOT NULL,
    currency            varchar(3)     NOT NULL,
    direction           char(1)        NOT NULL,
    amount              numeric(19, 4) NOT NULL,
    business_date       date           NOT NULL,
    narration           varchar(200),
    CONSTRAINT pk_ledger_entry PRIMARY KEY (id),
    CONSTRAINT uq_ledger_entry_line UNIQUE (journal_entry_id, line_no),
    CONSTRAINT fk_ledger_entry_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT fk_ledger_entry_gl FOREIGN KEY (tenant_id, chart_of_account_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_ledger_entry_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT fk_ledger_entry_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_ledger_entry_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT ck_ledger_entry_line_no CHECK (line_no > 0),
    CONSTRAINT ck_ledger_entry_direction CHECK (direction IN ('D', 'C')),
    CONSTRAINT ck_ledger_entry_amount CHECK (amount > 0)
);

CREATE INDEX ix_ledger_entry_journal ON core.ledger_entry (journal_entry_id);
CREATE INDEX ix_ledger_entry_account ON core.ledger_entry (tenant_id, ledger_account_id, business_date)
    WHERE ledger_account_id IS NOT NULL;
CREATE INDEX ix_ledger_entry_gl ON core.ledger_entry (tenant_id, chart_of_account_id, branch_id, business_date);
SELECT core.apply_tenant_isolation('core.ledger_entry');

GRANT SELECT, INSERT ON core.journal_entry TO ${app_db_role};
GRANT SELECT, INSERT ON core.ledger_entry TO ${app_db_role};

-- ------------------------------------------------------------------------------------------- functions

-- Locks the open period covering a date (shared lock: closing the period waits for this transaction).
CREATE FUNCTION core.require_open_period(p_tenant uuid, p_date date) RETURNS void
    LANGUAGE plpgsql
    SET search_path = core, pg_temp
AS
$$
BEGIN
    PERFORM 1
    FROM core.accounting_period
    WHERE tenant_id = p_tenant
      AND p_date BETWEEN period_start AND period_end
      AND status = 'OPEN'
        FOR SHARE;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No open accounting period for %', p_date USING ERRCODE = 'check_violation';
    END IF;
END;
$$;

CREATE FUNCTION core.journal_entry_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = core, pg_temp
AS
$$
BEGIN
    -- Stamped by the database, so lines can be tied to the transaction that created the journal.
    NEW.posting_txid := pg_current_xact_id();
    NEW.posted_at := now();
    PERFORM core.require_open_period(NEW.tenant_id, NEW.business_date);
    RETURN NEW;
END;
$$;

CREATE FUNCTION core.ledger_entry_before_insert() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_journal core.journal_entry%ROWTYPE;
    v_gl      core.chart_of_account%ROWTYPE;
    v_account core.ledger_account%ROWTYPE;
BEGIN
    SELECT * INTO v_journal FROM core.journal_entry WHERE id = NEW.journal_entry_id AND tenant_id = NEW.tenant_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'Journal % does not exist', NEW.journal_entry_id USING ERRCODE = 'foreign_key_violation';
    END IF;
    IF v_journal.posting_txid <> pg_current_xact_id() THEN
        RAISE EXCEPTION 'Lines can only be added to a journal in the transaction that created it'
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.business_date <> v_journal.business_date THEN
        RAISE EXCEPTION 'Line business date differs from its journal' USING ERRCODE = 'check_violation';
    END IF;
    PERFORM core.require_open_period(NEW.tenant_id, NEW.business_date);

    SELECT * INTO v_gl FROM core.chart_of_account WHERE id = NEW.chart_of_account_id AND tenant_id = NEW.tenant_id;
    IF v_gl.is_header OR v_gl.status <> 'ACTIVE' THEN
        RAISE EXCEPTION 'GL account % does not accept postings', v_gl.code USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.ledger_account_id IS NOT NULL THEN
        SELECT * INTO v_account FROM core.ledger_account WHERE id = NEW.ledger_account_id AND tenant_id = NEW.tenant_id;
        IF v_account.status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Ledger account % is closed', NEW.ledger_account_id USING ERRCODE = 'check_violation';
        END IF;
        IF v_account.chart_of_account_id <> NEW.chart_of_account_id
            OR v_account.branch_id <> NEW.branch_id
            OR v_account.currency <> NEW.currency THEN
            RAISE EXCEPTION 'Line does not match the GL account, branch or currency of its ledger account'
                USING ERRCODE = 'check_violation';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

-- Applies a line to the balance projection. SECURITY DEFINER: the application has no UPDATE right on the
-- balance itself, so this trigger is the only way it changes. A balance-checked account that would go below its
-- limit fails ck_account_balance_no_overdraft.
CREATE FUNCTION core.ledger_entry_apply_balance() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = core, pg_temp
AS
$$
BEGIN
    IF NEW.ledger_account_id IS NULL THEN
        RETURN NULL;
    END IF;
    UPDATE core.account_balance
    SET ledger_balance        = ledger_balance +
                                CASE WHEN (NEW.direction = 'D') = (normal_side = 'DEBIT')
                                         THEN NEW.amount
                                     ELSE -NEW.amount END,
        last_journal_entry_id = NEW.journal_entry_id,
        last_posted_at        = now(),
        version               = version + 1
    WHERE ledger_account_id = NEW.ledger_account_id
      AND tenant_id = NEW.tenant_id;
    IF NOT FOUND THEN
        RAISE EXCEPTION 'No balance row for ledger account %', NEW.ledger_account_id
            USING ERRCODE = 'foreign_key_violation';
    END IF;
    RETURN NULL;
END;
$$;

-- Runs at commit: the journal has at least two lines, balances per currency and per branch, and a reversal
-- exactly cancels its original line by line.
CREATE FUNCTION core.journal_entry_check_balanced() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_lines integer;
BEGIN
    SELECT count(*) INTO v_lines FROM core.ledger_entry WHERE journal_entry_id = NEW.id AND tenant_id = NEW.tenant_id;
    IF v_lines < 2 THEN
        RAISE EXCEPTION 'Journal % has % line(s); at least two are required', NEW.journal_number, v_lines
            USING ERRCODE = 'check_violation';
    END IF;

    IF EXISTS (SELECT 1
               FROM core.ledger_entry
               WHERE journal_entry_id = NEW.id
                 AND tenant_id = NEW.tenant_id
               GROUP BY currency, branch_id
               HAVING sum(CASE direction WHEN 'D' THEN amount ELSE -amount END) <> 0) THEN
        RAISE EXCEPTION 'Journal % does not balance per currency and branch', NEW.journal_number
            USING ERRCODE = 'check_violation';
    END IF;

    IF NEW.reverses_journal_id IS NOT NULL AND EXISTS (
        SELECT 1
        FROM core.ledger_entry
        WHERE journal_entry_id IN (NEW.id, NEW.reverses_journal_id)
          AND tenant_id = NEW.tenant_id
        GROUP BY chart_of_account_id, ledger_account_id, branch_id, currency
        HAVING sum(CASE direction WHEN 'D' THEN amount ELSE -amount END) <> 0) THEN
        RAISE EXCEPTION 'Reversal % does not mirror the journal it reverses', NEW.journal_number
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END;
$$;

-- A new sub-ledger account gets its balance row in the same transaction.
CREATE FUNCTION core.ledger_account_after_insert() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = core, pg_temp
AS
$$
BEGIN
    INSERT INTO core.account_balance (ledger_account_id, tenant_id, currency, normal_side, balance_check)
    VALUES (NEW.id, NEW.tenant_id, NEW.currency, NEW.normal_side, NEW.balance_check);
    RETURN NULL;
END;
$$;

CREATE FUNCTION core.ledger_account_before_write() RETURNS trigger
    LANGUAGE plpgsql
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_gl      core.chart_of_account%ROWTYPE;
    v_balance numeric(19, 4);
    v_holds   numeric(19, 4);
BEGIN
    IF TG_OP = 'INSERT' THEN
        SELECT * INTO v_gl FROM core.chart_of_account WHERE id = NEW.chart_of_account_id AND tenant_id = NEW.tenant_id;
        IF v_gl.is_header OR v_gl.status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Ledger accounts must roll up into an active posting GL account'
                USING ERRCODE = 'check_violation';
        END IF;
        IF v_gl.normal_side <> NEW.normal_side THEN
            RAISE EXCEPTION 'Ledger account normal side must match its GL account' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.status = 'CLOSED' AND OLD.status <> 'CLOSED' THEN
        SELECT ledger_balance, hold_amount INTO v_balance, v_holds
        FROM core.account_balance WHERE ledger_account_id = NEW.id AND tenant_id = NEW.tenant_id;
        IF v_balance <> 0 OR v_holds <> 0 THEN
            RAISE EXCEPTION 'Only a ledger account with zero balance and no holds can be closed'
                USING ERRCODE = 'check_violation';
        END IF;
    ELSIF OLD.status = 'CLOSED' THEN
        RAISE EXCEPTION 'A closed ledger account cannot change' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION core.require_open_period(uuid, date) FROM PUBLIC;
REVOKE ALL ON FUNCTION core.ledger_entry_apply_balance() FROM PUBLIC;
REVOKE ALL ON FUNCTION core.journal_entry_check_balanced() FROM PUBLIC;
REVOKE ALL ON FUNCTION core.ledger_account_after_insert() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.require_open_period(uuid, date) TO ${app_db_role};

-- -------------------------------------------------------------------------------------------- triggers

CREATE TRIGGER trg_journal_entry_before_insert
    BEFORE INSERT ON core.journal_entry
    FOR EACH ROW EXECUTE FUNCTION core.journal_entry_before_insert();
CREATE CONSTRAINT TRIGGER trg_journal_balanced
    AFTER INSERT ON core.journal_entry
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION core.journal_entry_check_balanced();
CREATE TRIGGER trg_journal_entry_immutable
    BEFORE UPDATE OR DELETE ON core.journal_entry
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
CREATE TRIGGER trg_journal_entry_no_truncate
    BEFORE TRUNCATE ON core.journal_entry
    FOR EACH STATEMENT EXECUTE FUNCTION core.reject_mutation();

CREATE TRIGGER trg_ledger_entry_before_insert
    BEFORE INSERT ON core.ledger_entry
    FOR EACH ROW EXECUTE FUNCTION core.ledger_entry_before_insert();
CREATE TRIGGER trg_ledger_entry_apply_balance
    AFTER INSERT ON core.ledger_entry
    FOR EACH ROW EXECUTE FUNCTION core.ledger_entry_apply_balance();
CREATE TRIGGER trg_ledger_entry_immutable
    BEFORE UPDATE OR DELETE ON core.ledger_entry
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
CREATE TRIGGER trg_ledger_entry_no_truncate
    BEFORE TRUNCATE ON core.ledger_entry
    FOR EACH STATEMENT EXECUTE FUNCTION core.reject_mutation();

CREATE TRIGGER trg_ledger_account_before_write
    BEFORE INSERT OR UPDATE ON core.ledger_account
    FOR EACH ROW EXECUTE FUNCTION core.ledger_account_before_write();
CREATE TRIGGER trg_ledger_account_after_insert
    AFTER INSERT ON core.ledger_account
    FOR EACH ROW EXECUTE FUNCTION core.ledger_account_after_insert();
CREATE TRIGGER trg_ledger_account_no_delete
    BEFORE DELETE ON core.ledger_account
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
CREATE TRIGGER trg_account_balance_no_delete
    BEFORE DELETE ON core.account_balance
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
