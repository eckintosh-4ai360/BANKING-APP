-- =====================================================================================================
-- V15 Deposit products and customer accounts.
--
-- Products are versioned: an account keeps the terms of the version it was opened under (decision D9), and a
-- published version never changes. Holds reserve funds; their total is maintained in account_balance by a
-- trigger, so the database's no-overdraft rule also stops a hold larger than the available balance.
-- =====================================================================================================

CREATE TABLE core.account_product
(
    id                 uuid         NOT NULL,
    tenant_id          uuid         NOT NULL,
    code               varchar(30)  NOT NULL,
    name               varchar(120) NOT NULL,
    product_type       varchar(20)  NOT NULL,
    description        varchar(500),
    status             varchar(10)  NOT NULL,
    current_version_id uuid,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    created_by         uuid,
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_product PRIMARY KEY (id),
    CONSTRAINT uq_account_product_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_account_product_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_account_product_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_account_product_code CHECK (code ~ '^[A-Z0-9][A-Z0-9_-]{1,29}$'),
    CONSTRAINT ck_account_product_type
        CHECK (product_type IN ('SAVINGS', 'CURRENT', 'SUSU', 'FIXED_DEPOSIT', 'TARGET_SAVINGS')),
    CONSTRAINT ck_account_product_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

SELECT core.apply_tenant_isolation('core.account_product');
GRANT SELECT, INSERT ON core.account_product TO ${app_db_role};
GRANT UPDATE (name, description, status, current_version_id, updated_at, updated_by, version)
    ON core.account_product TO ${app_db_role};

CREATE TABLE core.account_product_version
(
    id                         uuid           NOT NULL,
    tenant_id                  uuid           NOT NULL,
    product_id                 uuid           NOT NULL,
    version_no                 integer        NOT NULL,
    status                     varchar(10)    NOT NULL,
    currency                   varchar(3)     NOT NULL,
    deposit_gl_id              uuid           NOT NULL,
    fee_income_gl_id           uuid,
    interest_expense_gl_id     uuid,
    min_opening_balance        numeric(19, 4) NOT NULL DEFAULT 0,
    min_operating_balance      numeric(19, 4) NOT NULL DEFAULT 0,
    max_balance                numeric(19, 4),
    interest_rate              numeric(9, 6)  NOT NULL DEFAULT 0,
    interest_calc_method       varchar(25)    NOT NULL,
    interest_posting_frequency varchar(10)    NOT NULL,
    day_count                  varchar(12)    NOT NULL,
    dormancy_days              integer        NOT NULL,
    required_kyc_tier          varchar(20),
    allow_overdraft            boolean        NOT NULL DEFAULT false,
    max_overdraft_limit        numeric(19, 4) NOT NULL DEFAULT 0,
    max_withdrawal_amount      numeric(19, 4),
    daily_withdrawal_limit     numeric(19, 4),
    created_at                 timestamptz    NOT NULL,
    created_by                 uuid,
    published_at               timestamptz,
    published_by               uuid,
    CONSTRAINT pk_account_product_version PRIMARY KEY (id),
    CONSTRAINT uq_account_product_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_account_product_version_no UNIQUE (product_id, version_no),
    CONSTRAINT fk_account_product_version_product FOREIGN KEY (tenant_id, product_id)
        REFERENCES core.account_product (tenant_id, id),
    CONSTRAINT fk_account_product_version_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_account_product_version_deposit_gl FOREIGN KEY (tenant_id, deposit_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_account_product_version_fee_gl FOREIGN KEY (tenant_id, fee_income_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_account_product_version_interest_gl FOREIGN KEY (tenant_id, interest_expense_gl_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT ck_account_product_version_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT ck_account_product_version_published CHECK ((status = 'DRAFT') = (published_at IS NULL)),
    CONSTRAINT ck_account_product_version_amounts CHECK (
        min_opening_balance >= 0 AND min_operating_balance >= 0 AND max_overdraft_limit >= 0
            AND (max_balance IS NULL OR max_balance > 0)
            AND (max_withdrawal_amount IS NULL OR max_withdrawal_amount > 0)
            AND (daily_withdrawal_limit IS NULL OR daily_withdrawal_limit > 0)),
    CONSTRAINT ck_account_product_version_overdraft CHECK (allow_overdraft OR max_overdraft_limit = 0),
    CONSTRAINT ck_account_product_version_rate CHECK (interest_rate >= 0 AND interest_rate <= 100),
    CONSTRAINT ck_account_product_version_calc
        CHECK (interest_calc_method IN ('DAILY_BALANCE', 'MIN_MONTHLY_BALANCE', 'AVG_DAILY_BALANCE')),
    CONSTRAINT ck_account_product_version_posting
        CHECK (interest_posting_frequency IN ('NONE', 'MONTHLY', 'QUARTERLY', 'ANNUALLY')),
    CONSTRAINT ck_account_product_version_day_count CHECK (day_count IN ('ACTUAL_365F', 'ACTUAL_360', 'THIRTY_360')),
    CONSTRAINT ck_account_product_version_dormancy CHECK (dormancy_days BETWEEN 30 AND 3650)
);

SELECT core.apply_tenant_isolation('core.account_product_version');

ALTER TABLE core.account_product
    ADD CONSTRAINT fk_account_product_current_version FOREIGN KEY (tenant_id, current_version_id)
        REFERENCES core.account_product_version (tenant_id, id);

-- Published terms are a contract with the customers who opened accounts under them: only retiring is allowed.
CREATE FUNCTION core.account_product_version_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF OLD.status = 'RETIRED' THEN
        RAISE EXCEPTION 'A retired product version cannot change' USING ERRCODE = 'check_violation';
    END IF;
    IF OLD.status = 'PUBLISHED' AND (NEW.status <> 'RETIRED'
        OR (to_jsonb(NEW) - 'status') <> (to_jsonb(OLD) - 'status')) THEN
        RAISE EXCEPTION 'A published product version can only be retired' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_account_product_version_guard
    BEFORE UPDATE ON core.account_product_version
    FOR EACH ROW EXECUTE FUNCTION core.account_product_version_guard();
CREATE TRIGGER trg_account_product_version_no_delete
    BEFORE DELETE ON core.account_product_version
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();

GRANT SELECT, INSERT, UPDATE ON core.account_product_version TO ${app_db_role};

-- ------------------------------------------------------------------------------------------------ account

CREATE TABLE core.account
(
    id                 uuid         NOT NULL,
    tenant_id          uuid         NOT NULL,
    account_number     varchar(20)  NOT NULL,
    customer_id        uuid         NOT NULL,
    product_id         uuid         NOT NULL,
    product_version_id uuid         NOT NULL,
    ledger_account_id  uuid         NOT NULL,
    branch_id          uuid         NOT NULL,
    currency           varchar(3)   NOT NULL,
    title              varchar(150) NOT NULL,
    status             varchar(12)  NOT NULL,
    status_reason      varchar(300),
    ownership_type     varchar(12)  NOT NULL,
    opened_on          date         NOT NULL,
    activated_at       timestamptz,
    closed_on          date,
    last_activity_at   timestamptz,
    created_at         timestamptz  NOT NULL,
    updated_at         timestamptz  NOT NULL,
    created_by         uuid,
    updated_by         uuid,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_account PRIMARY KEY (id),
    CONSTRAINT uq_account_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_account_number UNIQUE (tenant_id, account_number),
    CONSTRAINT uq_account_ledger_account UNIQUE (tenant_id, ledger_account_id),
    CONSTRAINT fk_account_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_account_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_account_product FOREIGN KEY (tenant_id, product_id) REFERENCES core.account_product (tenant_id, id),
    CONSTRAINT fk_account_product_version FOREIGN KEY (tenant_id, product_version_id)
        REFERENCES core.account_product_version (tenant_id, id),
    CONSTRAINT fk_account_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT fk_account_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_account_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT ck_account_number CHECK (account_number ~ '^[0-9]{10,20}$'),
    CONSTRAINT ck_account_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'RESTRICTED', 'FROZEN', 'DORMANT', 'CLOSED')),
    CONSTRAINT ck_account_ownership CHECK (ownership_type IN ('SINGLE', 'JOINT_ANY', 'JOINT_ALL', 'BUSINESS')),
    CONSTRAINT ck_account_closed CHECK ((status = 'CLOSED') = (closed_on IS NOT NULL))
);

CREATE INDEX ix_account_customer ON core.account (tenant_id, customer_id);
CREATE INDEX ix_account_branch_status ON core.account (tenant_id, branch_id, status);
SELECT core.apply_tenant_isolation('core.account');

GRANT SELECT, INSERT ON core.account TO ${app_db_role};
GRANT UPDATE (title, status, status_reason, activated_at, closed_on, last_activity_at, updated_at, updated_by, version)
    ON core.account TO ${app_db_role};

CREATE TABLE core.account_holder
(
    account_id  uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    customer_id uuid        NOT NULL,
    holder_role varchar(12) NOT NULL,
    added_at    timestamptz NOT NULL,
    added_by    uuid,
    removed_at  timestamptz,
    CONSTRAINT pk_account_holder PRIMARY KEY (account_id, customer_id),
    CONSTRAINT fk_account_holder_account FOREIGN KEY (tenant_id, account_id) REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_account_holder_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_account_holder_role CHECK (holder_role IN ('PRIMARY', 'JOINT', 'SIGNATORY'))
);

CREATE INDEX ix_account_holder_customer ON core.account_holder (tenant_id, customer_id);
CREATE UNIQUE INDEX uq_account_holder_primary ON core.account_holder (account_id)
    WHERE holder_role = 'PRIMARY' AND removed_at IS NULL;
SELECT core.apply_tenant_isolation('core.account_holder');
GRANT SELECT, INSERT ON core.account_holder TO ${app_db_role};
GRANT UPDATE (removed_at) ON core.account_holder TO ${app_db_role};

-- ----------------------------------------------------------------------------------------------- account_hold

CREATE TABLE core.account_hold
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    account_id        uuid           NOT NULL,
    ledger_account_id uuid           NOT NULL,
    amount            numeric(19, 4) NOT NULL,
    hold_type         varchar(20)    NOT NULL,
    status            varchar(10)    NOT NULL,
    reason            varchar(300)   NOT NULL,
    reference         varchar(60),
    expires_at        timestamptz,
    placed_at         timestamptz    NOT NULL,
    placed_by         uuid,
    released_at       timestamptz,
    released_by       uuid,
    release_reason    varchar(300),
    version           bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_account_hold PRIMARY KEY (id),
    CONSTRAINT fk_account_hold_account FOREIGN KEY (tenant_id, account_id) REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_account_hold_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT ck_account_hold_amount CHECK (amount > 0),
    CONSTRAINT ck_account_hold_type
        CHECK (hold_type IN ('LIEN', 'PENDING_PAYMENT', 'LEGAL', 'FRAUD_REVIEW', 'LOAN_COLLATERAL')),
    CONSTRAINT ck_account_hold_status CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED', 'EXPIRED')),
    CONSTRAINT ck_account_hold_released CHECK ((status = 'ACTIVE') = (released_at IS NULL))
);

CREATE INDEX ix_account_hold_account ON core.account_hold (tenant_id, account_id, status);
SELECT core.apply_tenant_isolation('core.account_hold');
GRANT SELECT, INSERT ON core.account_hold TO ${app_db_role};
GRANT UPDATE (status, released_at, released_by, release_reason, version) ON core.account_hold TO ${app_db_role};

-- Keeps account_balance.hold_amount equal to the sum of active holds. An active hold may only end, never come back
-- or change its amount.
CREATE FUNCTION core.account_hold_apply() RETURNS trigger
    LANGUAGE plpgsql
    SECURITY DEFINER
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_delta numeric(19, 4) := 0;
BEGIN
    IF TG_OP = 'INSERT' THEN
        IF NEW.status <> 'ACTIVE' THEN
            RAISE EXCEPTION 'Holds are created active' USING ERRCODE = 'check_violation';
        END IF;
        v_delta := NEW.amount;
    ELSE
        IF OLD.status <> 'ACTIVE' OR NEW.amount <> OLD.amount OR NEW.ledger_account_id <> OLD.ledger_account_id THEN
            RAISE EXCEPTION 'Only an active hold can end, and its amount cannot change'
                USING ERRCODE = 'check_violation';
        END IF;
        IF NEW.status <> 'ACTIVE' THEN
            v_delta := -OLD.amount;
        END IF;
    END IF;
    IF v_delta <> 0 THEN
        UPDATE core.account_balance
        SET hold_amount = hold_amount + v_delta,
            version     = version + 1
        WHERE ledger_account_id = NEW.ledger_account_id
          AND tenant_id = NEW.tenant_id;
    END IF;
    RETURN NEW;
END;
$$;

REVOKE ALL ON FUNCTION core.account_hold_apply() FROM PUBLIC;

CREATE TRIGGER trg_account_hold_apply
    BEFORE INSERT OR UPDATE ON core.account_hold
    FOR EACH ROW EXECUTE FUNCTION core.account_hold_apply();
CREATE TRIGGER trg_account_hold_no_delete
    BEFORE DELETE ON core.account_hold
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
