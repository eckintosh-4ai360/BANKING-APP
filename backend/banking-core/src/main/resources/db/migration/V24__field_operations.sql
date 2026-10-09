-- =====================================================================================================
-- V24 Field operations: field officers and the cash they carry, customer assignments, devices, offline
-- collections, visits and alerts.
--
-- An officer's cash is a balance-checked ledger account (cash with collectors): every collection raises it and only
-- a remittance to a teller lowers it. Collections arrive from devices, possibly long after they were taken, and
-- are idempotent on the client's reference; each device numbers its collections 1, 2, 3, ... so a gap shows a
-- collection that never arrived.
-- =====================================================================================================

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('field.manage', 'field', 'Manage field officers, customer assignments, devices and field alerts', 'TENANT', true);

-- Field collections are customer transactions of their own type.
ALTER TABLE core.financial_transaction DROP CONSTRAINT ck_financial_transaction_type;
ALTER TABLE core.financial_transaction ADD CONSTRAINT ck_financial_transaction_type
    CHECK (transaction_type IN ('CASH_DEPOSIT', 'CASH_WITHDRAWAL', 'TRANSFER', 'FIELD_COLLECTION'));
ALTER TABLE core.financial_transaction DROP CONSTRAINT ck_financial_transaction_accounts;
ALTER TABLE core.financial_transaction ADD CONSTRAINT ck_financial_transaction_accounts CHECK (
    (transaction_type IN ('CASH_DEPOSIT', 'FIELD_COLLECTION') AND credit_account_id IS NOT NULL
        AND debit_account_id IS NULL)
        OR (transaction_type = 'CASH_WITHDRAWAL' AND debit_account_id IS NOT NULL AND credit_account_id IS NULL)
        OR (transaction_type = 'TRANSFER' AND debit_account_id IS NOT NULL AND credit_account_id IS NOT NULL
        AND debit_account_id <> credit_account_id));

CREATE TABLE core.field_officer
(
    staff_id                    uuid           NOT NULL,
    tenant_id                   uuid           NOT NULL,
    branch_id                   uuid           NOT NULL,
    currency                    varchar(3)     NOT NULL,
    collector_ledger_account_id uuid           NOT NULL,
    daily_target                numeric(19, 4),
    max_offline_amount          numeric(19, 4) NOT NULL,
    max_offline_hours           integer        NOT NULL,
    status                      varchar(10)    NOT NULL,
    created_at                  timestamptz    NOT NULL,
    created_by                  uuid,
    updated_at                  timestamptz    NOT NULL,
    version                     bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_field_officer PRIMARY KEY (staff_id),
    CONSTRAINT uq_field_officer_tenant_staff UNIQUE (tenant_id, staff_id),
    CONSTRAINT uq_field_officer_ledger_account UNIQUE (tenant_id, collector_ledger_account_id),
    CONSTRAINT fk_field_officer_staff FOREIGN KEY (tenant_id, staff_id) REFERENCES core.staff (tenant_id, id),
    CONSTRAINT fk_field_officer_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_field_officer_currency FOREIGN KEY (currency) REFERENCES core.currency (code),
    CONSTRAINT fk_field_officer_ledger_account FOREIGN KEY (tenant_id, collector_ledger_account_id)
        REFERENCES core.ledger_account (tenant_id, id),
    CONSTRAINT ck_field_officer_status CHECK (status IN ('ACTIVE', 'SUSPENDED')),
    CONSTRAINT ck_field_officer_limits CHECK (max_offline_amount > 0 AND max_offline_hours BETWEEN 1 AND 720
        AND (daily_target IS NULL OR daily_target > 0))
);

SELECT core.apply_tenant_isolation('core.field_officer');
GRANT SELECT, INSERT ON core.field_officer TO ${app_db_role};
GRANT UPDATE (daily_target, max_offline_amount, max_offline_hours, status, updated_at, version)
    ON core.field_officer TO ${app_db_role};

CREATE TABLE core.customer_assignment
(
    id            uuid         NOT NULL,
    tenant_id     uuid         NOT NULL,
    customer_id   uuid         NOT NULL,
    officer_id    uuid         NOT NULL,
    assigned_at   timestamptz  NOT NULL,
    assigned_by   uuid,
    ended_at      timestamptz,
    ended_by      uuid,
    end_reason    varchar(300),
    CONSTRAINT pk_customer_assignment PRIMARY KEY (id),
    CONSTRAINT fk_customer_assignment_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_assignment_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT ck_customer_assignment_end CHECK (ended_at IS NOT NULL OR (ended_by IS NULL AND end_reason IS NULL))
);

-- One officer at a time looks after a customer.
CREATE UNIQUE INDEX uq_customer_assignment_active ON core.customer_assignment (tenant_id, customer_id)
    WHERE ended_at IS NULL;
CREATE INDEX ix_customer_assignment_officer ON core.customer_assignment (tenant_id, officer_id)
    WHERE ended_at IS NULL;
SELECT core.apply_tenant_isolation('core.customer_assignment');
CREATE TRIGGER trg_customer_assignment_no_delete
    BEFORE DELETE ON core.customer_assignment
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.customer_assignment TO ${app_db_role};
GRANT UPDATE (ended_at, ended_by, end_reason) ON core.customer_assignment TO ${app_db_role};

CREATE TABLE core.field_device
(
    id                uuid         NOT NULL,
    tenant_id         uuid         NOT NULL,
    officer_id        uuid         NOT NULL,
    device_key        varchar(100) NOT NULL,
    name              varchar(100) NOT NULL,
    last_sequence_no  bigint       NOT NULL DEFAULT 0,
    registered_at     timestamptz  NOT NULL,
    last_synced_at    timestamptz,
    revoked_at        timestamptz,
    revoked_by        uuid,
    revoke_reason     varchar(300),
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_field_device PRIMARY KEY (id),
    CONSTRAINT uq_field_device_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_field_device_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT ck_field_device_key CHECK (device_key ~ '^[A-Za-z0-9_-]{8,100}$'),
    CONSTRAINT ck_field_device_sequence CHECK (last_sequence_no >= 0)
);

COMMENT ON COLUMN core.field_device.device_key IS 'Installation id generated by the app; one live registration at a time';
COMMENT ON COLUMN core.field_device.last_sequence_no IS 'Highest collection sequence number received from the device';

CREATE UNIQUE INDEX uq_field_device_live ON core.field_device (tenant_id, device_key) WHERE revoked_at IS NULL;
SELECT core.apply_tenant_isolation('core.field_device');
CREATE TRIGGER trg_field_device_no_delete
    BEFORE DELETE ON core.field_device
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.field_device TO ${app_db_role};
GRANT UPDATE (name, last_sequence_no, last_synced_at, revoked_at, revoked_by, revoke_reason, version)
    ON core.field_device TO ${app_db_role};

-- One row per collection that reached the server, posted or rejected. A resubmission of the same client reference
-- is answered from this row; a different collection under a used reference or sequence number is a conflict and
-- raises an alert instead. Customer, account and plan are kept as the device sent them (no foreign keys), so even a
-- collection naming something unknown is recorded and its sequence number accounted for; a posted collection is
-- tied to its financial transaction.
CREATE TABLE core.collection
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    client_reference         uuid           NOT NULL,
    device_id                uuid           NOT NULL,
    device_sequence_no       bigint         NOT NULL,
    officer_id               uuid           NOT NULL,
    customer_id              uuid           NOT NULL,
    target_type              varchar(20)    NOT NULL,
    account_id               uuid           NOT NULL,
    susu_plan_id             uuid,
    amount                   numeric(19, 4) NOT NULL,
    currency                 varchar(3)     NOT NULL,
    collected_at             timestamptz    NOT NULL,
    received_at              timestamptz    NOT NULL,
    latitude                 numeric(9, 6),
    longitude                numeric(9, 6),
    note                     varchar(200),
    content_hash             varchar(64)    NOT NULL,
    status                   varchar(10)    NOT NULL,
    rejection_code           varchar(60),
    rejection_reason         varchar(300),
    financial_transaction_id uuid,
    transaction_reference    varchar(40),
    business_date            date,
    CONSTRAINT pk_collection PRIMARY KEY (id),
    CONSTRAINT uq_collection_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_collection_client_reference UNIQUE (tenant_id, client_reference),
    CONSTRAINT uq_collection_device_sequence UNIQUE (device_id, device_sequence_no),
    CONSTRAINT fk_collection_device FOREIGN KEY (tenant_id, device_id) REFERENCES core.field_device (tenant_id, id),
    CONSTRAINT fk_collection_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT fk_collection_transaction FOREIGN KEY (tenant_id, financial_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id),
    CONSTRAINT ck_collection_target CHECK (target_type IN ('SAVINGS_ACCOUNT', 'SUSU_PLAN')
        AND (target_type = 'SUSU_PLAN') = (susu_plan_id IS NOT NULL)),
    CONSTRAINT ck_collection_amount CHECK (amount > 0),
    CONSTRAINT ck_collection_sequence CHECK (device_sequence_no > 0),
    CONSTRAINT ck_collection_status CHECK (status IN ('POSTED', 'REJECTED')),
    CONSTRAINT ck_collection_outcome CHECK (
        (status = 'POSTED' AND financial_transaction_id IS NOT NULL AND transaction_reference IS NOT NULL
            AND business_date IS NOT NULL AND rejection_code IS NULL)
            OR (status = 'REJECTED' AND financial_transaction_id IS NULL AND transaction_reference IS NULL
            AND rejection_code IS NOT NULL)),
    CONSTRAINT ck_collection_location CHECK ((latitude IS NULL) = (longitude IS NULL)
        AND (latitude IS NULL OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)))
);

CREATE INDEX ix_collection_officer_date ON core.collection (tenant_id, officer_id, business_date);
CREATE INDEX ix_collection_customer ON core.collection (tenant_id, customer_id, collected_at DESC);
SELECT core.apply_tenant_isolation('core.collection');
CREATE TRIGGER trg_collection_immutable
    BEFORE UPDATE OR DELETE ON core.collection
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.collection TO ${app_db_role};

CREATE TABLE core.customer_visit
(
    id               uuid          NOT NULL,
    tenant_id        uuid          NOT NULL,
    client_reference uuid          NOT NULL,
    device_id        uuid          NOT NULL,
    officer_id       uuid          NOT NULL,
    customer_id      uuid          NOT NULL,
    purpose          varchar(20)   NOT NULL,
    outcome          varchar(20)   NOT NULL,
    notes            varchar(1000),
    visited_at       timestamptz   NOT NULL,
    received_at      timestamptz   NOT NULL,
    latitude         numeric(9, 6),
    longitude        numeric(9, 6),
    content_hash     varchar(64)   NOT NULL,
    CONSTRAINT pk_customer_visit PRIMARY KEY (id),
    CONSTRAINT uq_customer_visit_client_reference UNIQUE (tenant_id, client_reference),
    CONSTRAINT fk_customer_visit_device FOREIGN KEY (tenant_id, device_id) REFERENCES core.field_device (tenant_id, id),
    CONSTRAINT fk_customer_visit_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT fk_customer_visit_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_customer_visit_purpose CHECK (purpose IN ('COLLECTION', 'ONBOARDING', 'FOLLOW_UP', 'LOAN_MONITORING',
        'RECOVERY', 'OTHER')),
    CONSTRAINT ck_customer_visit_outcome CHECK (outcome IN ('MET', 'NOT_AVAILABLE', 'PROMISED_TO_PAY', 'REFUSED',
        'RELOCATED', 'OTHER')),
    CONSTRAINT ck_customer_visit_location CHECK ((latitude IS NULL) = (longitude IS NULL)
        AND (latitude IS NULL OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)))
);

CREATE INDEX ix_customer_visit_customer ON core.customer_visit (tenant_id, customer_id, visited_at DESC);
CREATE INDEX ix_customer_visit_officer ON core.customer_visit (tenant_id, officer_id, visited_at DESC);
SELECT core.apply_tenant_isolation('core.customer_visit');
CREATE TRIGGER trg_customer_visit_immutable
    BEFORE UPDATE OR DELETE ON core.customer_visit
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.customer_visit TO ${app_db_role};

-- Signals that need a supervisor's attention. A sequence gap closes by itself once the missing collections arrive;
-- the others are closed by a supervisor with a note.
CREATE TABLE core.field_alert
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    officer_id      uuid         NOT NULL,
    device_id       uuid,
    alert_type      varchar(20)  NOT NULL,
    detail          varchar(500) NOT NULL,
    missing_from    bigint,
    missing_to      bigint,
    client_reference uuid,
    status          varchar(10)  NOT NULL,
    raised_at       timestamptz  NOT NULL,
    resolved_at     timestamptz,
    resolved_by     uuid,
    resolution      varchar(300),
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_field_alert PRIMARY KEY (id),
    CONSTRAINT fk_field_alert_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT fk_field_alert_device FOREIGN KEY (tenant_id, device_id) REFERENCES core.field_device (tenant_id, id),
    CONSTRAINT ck_field_alert_type CHECK (alert_type IN ('SEQUENCE_GAP', 'CONFLICT', 'LATE_SYNC', 'OFFLINE_LIMIT')),
    CONSTRAINT ck_field_alert_gap CHECK ((alert_type = 'SEQUENCE_GAP')
        = (missing_from IS NOT NULL AND missing_to IS NOT NULL AND missing_from <= missing_to AND device_id IS NOT NULL)),
    CONSTRAINT ck_field_alert_status CHECK (status IN ('OPEN', 'RESOLVED')),
    CONSTRAINT ck_field_alert_resolved CHECK ((status = 'RESOLVED') = (resolved_at IS NOT NULL))
);

CREATE INDEX ix_field_alert_open ON core.field_alert (tenant_id, officer_id) WHERE status = 'OPEN';
SELECT core.apply_tenant_isolation('core.field_alert');
CREATE TRIGGER trg_field_alert_no_delete
    BEFORE DELETE ON core.field_alert
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.field_alert TO ${app_db_role};
GRANT UPDATE (status, resolved_at, resolved_by, resolution, version) ON core.field_alert TO ${app_db_role};

-- Cash an officer hands to a teller: Dr the teller's drawer, Cr the officer's cash with collectors. The teller who
-- counts and receives it is never the officer.
CREATE TABLE core.collector_remittance
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    reference         varchar(40)    NOT NULL,
    officer_id        uuid           NOT NULL,
    teller_id         uuid           NOT NULL,
    teller_session_id uuid           NOT NULL,
    cash_drawer_id    uuid           NOT NULL,
    branch_id         uuid           NOT NULL,
    amount            numeric(19, 4) NOT NULL,
    currency          varchar(3)     NOT NULL,
    journal_entry_id  uuid           NOT NULL,
    business_date     date           NOT NULL,
    note              varchar(300),
    idempotency_key   varchar(100)   NOT NULL,
    created_at        timestamptz    NOT NULL,
    CONSTRAINT pk_collector_remittance PRIMARY KEY (id),
    CONSTRAINT uq_collector_remittance_reference UNIQUE (tenant_id, reference),
    CONSTRAINT fk_collector_remittance_officer FOREIGN KEY (tenant_id, officer_id)
        REFERENCES core.field_officer (tenant_id, staff_id),
    CONSTRAINT fk_collector_remittance_session FOREIGN KEY (tenant_id, teller_session_id)
        REFERENCES core.teller_session (tenant_id, id),
    CONSTRAINT fk_collector_remittance_drawer FOREIGN KEY (tenant_id, cash_drawer_id)
        REFERENCES core.cash_drawer (tenant_id, id),
    CONSTRAINT fk_collector_remittance_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_collector_remittance_amount CHECK (amount > 0),
    CONSTRAINT ck_collector_remittance_four_eyes CHECK (teller_id <> officer_id)
);

CREATE INDEX ix_collector_remittance_officer ON core.collector_remittance (tenant_id, officer_id, business_date);
SELECT core.apply_tenant_isolation('core.collector_remittance');
CREATE TRIGGER trg_collector_remittance_immutable
    BEFORE UPDATE OR DELETE ON core.collector_remittance
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.collector_remittance TO ${app_db_role};
