-- =====================================================================================================
-- V20 Vaults, cash drawers (tills), teller sessions, cash counts and cash movements.
--
-- Every vault and drawer is a balance-checked ledger account, so cash can never go below zero and the expected
-- cash of a drawer is always its ledger balance. Teller cash transactions post to the teller's open drawer.
-- =====================================================================================================

CREATE TABLE core.vault
(
    id                uuid         NOT NULL,
    tenant_id         uuid         NOT NULL,
    branch_id         uuid         NOT NULL,
    currency          varchar(3)   NOT NULL,
    name              varchar(100) NOT NULL,
    ledger_account_id uuid         NOT NULL,
    status            varchar(10)  NOT NULL,
    created_at        timestamptz  NOT NULL,
    created_by        uuid,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_vault PRIMARY KEY (id),
    CONSTRAINT uq_vault_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_vault_ledger_account UNIQUE (tenant_id, ledger_account_id),
    CONSTRAINT fk_vault_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_vault_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_vault_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT ck_vault_status CHECK (status IN ('ACTIVE', 'CLOSED'))
);

-- One active vault per branch and currency.
CREATE UNIQUE INDEX uq_vault_branch_currency ON core.vault (tenant_id, branch_id, currency) WHERE status = 'ACTIVE';
SELECT core.apply_tenant_isolation('core.vault');
GRANT SELECT, INSERT ON core.vault TO ${app_db_role};
GRANT UPDATE (name, status, version) ON core.vault TO ${app_db_role};

CREATE TABLE core.cash_drawer
(
    id                uuid         NOT NULL,
    tenant_id         uuid         NOT NULL,
    branch_id         uuid         NOT NULL,
    currency          varchar(3)   NOT NULL,
    code              varchar(20)  NOT NULL,
    name              varchar(100) NOT NULL,
    ledger_account_id uuid         NOT NULL,
    status            varchar(10)  NOT NULL,
    created_at        timestamptz  NOT NULL,
    created_by        uuid,
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_drawer PRIMARY KEY (id),
    CONSTRAINT uq_cash_drawer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_cash_drawer_code UNIQUE (tenant_id, branch_id, code),
    CONSTRAINT uq_cash_drawer_ledger_account UNIQUE (tenant_id, ledger_account_id),
    CONSTRAINT fk_cash_drawer_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_cash_drawer_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_cash_drawer_ledger_account FOREIGN KEY (tenant_id, ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT ck_cash_drawer_code CHECK (code ~ '^[A-Z0-9][A-Z0-9-]{0,19}$'),
    CONSTRAINT ck_cash_drawer_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED'))
);

SELECT core.apply_tenant_isolation('core.cash_drawer');
GRANT SELECT, INSERT ON core.cash_drawer TO ${app_db_role};
GRANT UPDATE (name, status, version) ON core.cash_drawer TO ${app_db_role};

-- ---------------------------------------------------------------------------------------------- sessions

CREATE TABLE core.teller_session
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    branch_id                uuid           NOT NULL,
    cash_drawer_id           uuid           NOT NULL,
    teller_id                uuid           NOT NULL,
    business_date            date           NOT NULL,
    status                   varchar(25)    NOT NULL,
    currency                 varchar(3)     NOT NULL,
    opening_balance          numeric(19, 4) NOT NULL,
    expected_closing_balance numeric(19, 4),
    counted_balance          numeric(19, 4),
    difference               numeric(19, 4),
    opened_at                timestamptz    NOT NULL,
    closing_requested_at     timestamptz,
    closed_at                timestamptz,
    supervisor_id            uuid,
    difference_journal_id    uuid,
    close_note               varchar(300),
    version                  bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_teller_session PRIMARY KEY (id),
    CONSTRAINT uq_teller_session_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_teller_session_drawer FOREIGN KEY (tenant_id, cash_drawer_id)
        REFERENCES core.cash_drawer (tenant_id, id),
    CONSTRAINT fk_teller_session_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_teller_session_journal FOREIGN KEY (tenant_id, difference_journal_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_teller_session_status
        CHECK (status IN ('OPEN', 'BALANCING', 'CLOSED', 'CLOSED_WITH_DIFFERENCE')),
    CONSTRAINT ck_teller_session_difference CHECK (difference IS NULL
        OR difference = counted_balance - expected_closing_balance),
    CONSTRAINT ck_teller_session_closed CHECK ((status IN ('CLOSED', 'CLOSED_WITH_DIFFERENCE')) = (closed_at IS NOT NULL)),
    -- The supervisor who accepts a difference is never the teller.
    CONSTRAINT ck_teller_session_four_eyes CHECK (supervisor_id IS NULL OR supervisor_id <> teller_id)
);

-- One session at a time per drawer and per teller.
CREATE UNIQUE INDEX uq_teller_session_open_drawer ON core.teller_session (tenant_id, cash_drawer_id)
    WHERE status IN ('OPEN', 'BALANCING');
CREATE UNIQUE INDEX uq_teller_session_open_teller ON core.teller_session (tenant_id, teller_id)
    WHERE status IN ('OPEN', 'BALANCING');
CREATE INDEX ix_teller_session_date ON core.teller_session (tenant_id, business_date, branch_id);
SELECT core.apply_tenant_isolation('core.teller_session');
CREATE TRIGGER trg_teller_session_no_delete
    BEFORE DELETE ON core.teller_session
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.teller_session TO ${app_db_role};
GRANT UPDATE (status, expected_closing_balance, counted_balance, difference, closing_requested_at, closed_at,
              supervisor_id, difference_journal_id, close_note, version)
    ON core.teller_session TO ${app_db_role};

CREATE TABLE core.cash_count
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    teller_session_id uuid           NOT NULL,
    count_type        varchar(10)    NOT NULL,
    denominations     jsonb          NOT NULL,
    total             numeric(19, 4) NOT NULL,
    counted_by        uuid           NOT NULL,
    counted_at        timestamptz    NOT NULL,
    CONSTRAINT pk_cash_count PRIMARY KEY (id),
    CONSTRAINT fk_cash_count_session FOREIGN KEY (tenant_id, teller_session_id)
        REFERENCES core.teller_session (tenant_id, id),
    CONSTRAINT ck_cash_count_type CHECK (count_type IN ('OPENING', 'CLOSING')),
    CONSTRAINT ck_cash_count_total CHECK (total >= 0)
);

SELECT core.apply_tenant_isolation('core.cash_count');
CREATE TRIGGER trg_cash_count_immutable
    BEFORE UPDATE OR DELETE ON core.cash_count
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.cash_count TO ${app_db_role};

-- ------------------------------------------------------------------------------------------ cash movements

CREATE TABLE core.cash_movement
(
    id                      uuid           NOT NULL,
    tenant_id               uuid           NOT NULL,
    reference               varchar(40)    NOT NULL,
    movement_type           varchar(20)    NOT NULL,
    status                  varchar(12)    NOT NULL,
    currency                varchar(3)     NOT NULL,
    amount                  numeric(19, 4) NOT NULL,
    from_branch_id          uuid           NOT NULL,
    to_branch_id            uuid           NOT NULL,
    from_vault_id           uuid,
    from_drawer_id          uuid,
    to_vault_id             uuid,
    to_drawer_id            uuid,
    note                    varchar(300),
    requested_by            uuid           NOT NULL,
    requested_at            timestamptz    NOT NULL,
    approved_by             uuid,
    approved_at             timestamptz,
    dispatch_journal_id     uuid,
    received_by             uuid,
    received_at             timestamptz,
    receipt_journal_id      uuid,
    rejection_reason        varchar(300),
    version                 bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_cash_movement PRIMARY KEY (id),
    CONSTRAINT uq_cash_movement_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_cash_movement_reference UNIQUE (tenant_id, reference),
    CONSTRAINT fk_cash_movement_from_branch FOREIGN KEY (tenant_id, from_branch_id)
        REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_cash_movement_to_branch FOREIGN KEY (tenant_id, to_branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_cash_movement_from_vault FOREIGN KEY (tenant_id, from_vault_id) REFERENCES core.vault (tenant_id, id),
    CONSTRAINT fk_cash_movement_to_vault FOREIGN KEY (tenant_id, to_vault_id) REFERENCES core.vault (tenant_id, id),
    CONSTRAINT fk_cash_movement_from_drawer FOREIGN KEY (tenant_id, from_drawer_id)
        REFERENCES core.cash_drawer (tenant_id, id),
    CONSTRAINT fk_cash_movement_to_drawer FOREIGN KEY (tenant_id, to_drawer_id)
        REFERENCES core.cash_drawer (tenant_id, id),
    CONSTRAINT fk_cash_movement_dispatch_journal FOREIGN KEY (tenant_id, dispatch_journal_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT fk_cash_movement_receipt_journal FOREIGN KEY (tenant_id, receipt_journal_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_cash_movement_type CHECK (movement_type IN
                                             ('VAULT_TO_DRAWER', 'DRAWER_TO_VAULT', 'VAULT_TO_VAULT', 'BANK_TO_VAULT',
                                              'VAULT_TO_BANK')),
    CONSTRAINT ck_cash_movement_status
        CHECK (status IN ('REQUESTED', 'IN_TRANSIT', 'COMPLETED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT ck_cash_movement_amount CHECK (amount > 0),
    CONSTRAINT ck_cash_movement_four_eyes CHECK (approved_by IS NULL OR approved_by <> requested_by)
);

CREATE INDEX ix_cash_movement_status ON core.cash_movement (tenant_id, status, from_branch_id, to_branch_id);
SELECT core.apply_tenant_isolation('core.cash_movement');
CREATE TRIGGER trg_cash_movement_no_delete
    BEFORE DELETE ON core.cash_movement
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.cash_movement TO ${app_db_role};
GRANT UPDATE (status, approved_by, approved_at, dispatch_journal_id, received_by, received_at, receipt_journal_id,
              rejection_reason, version)
    ON core.cash_movement TO ${app_db_role};

-- Cash transactions remember the drawer their cash went through.
ALTER TABLE core.financial_transaction
    ADD COLUMN cash_drawer_id uuid,
    ADD CONSTRAINT fk_financial_transaction_drawer FOREIGN KEY (tenant_id, cash_drawer_id)
        REFERENCES core.cash_drawer (tenant_id, id);
