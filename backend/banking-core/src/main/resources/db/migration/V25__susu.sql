-- =====================================================================================================
-- V25 Susu: contribution plans on susu accounts, their schedules and cycle commission.
--
-- A plan asks a customer to contribute a fixed amount at a fixed frequency into their susu account. Contributions
-- are scheduled cycle by cycle (e.g. 31 daily contributions a month); field collections pay the oldest unpaid ones.
-- At the end of each cycle the collector's commission (N contributions per cycle) is charged to the account.
-- Frequencies are institution configuration, not code.
-- =====================================================================================================

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('susu.view', 'susu', 'View susu plans and contributions', 'TENANT', false),
       ('susu.manage', 'susu', 'Open, cancel and waive susu plans and configure susu frequencies', 'TENANT', true);

CREATE TABLE core.susu_frequency
(
    tenant_id      uuid         NOT NULL,
    code           varchar(20)  NOT NULL,
    name           varchar(60)  NOT NULL,
    interval_unit  varchar(5)   NOT NULL,
    interval_count integer      NOT NULL,
    active         boolean      NOT NULL DEFAULT true,
    created_at     timestamptz  NOT NULL,
    CONSTRAINT pk_susu_frequency PRIMARY KEY (tenant_id, code),
    CONSTRAINT fk_susu_frequency_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_susu_frequency_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,19}$'),
    CONSTRAINT ck_susu_frequency_interval CHECK (interval_unit IN ('DAY', 'WEEK', 'MONTH')
        AND interval_count BETWEEN 1 AND 12)
);

SELECT core.apply_tenant_isolation('core.susu_frequency');
GRANT SELECT, INSERT ON core.susu_frequency TO ${app_db_role};
GRANT UPDATE (name, active) ON core.susu_frequency TO ${app_db_role};

CREATE TABLE core.susu_plan
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    plan_number              varchar(30)    NOT NULL,
    customer_id              uuid           NOT NULL,
    account_id               uuid           NOT NULL,
    branch_id                uuid           NOT NULL,
    frequency_code           varchar(20)    NOT NULL,
    contribution_amount      numeric(19, 4) NOT NULL,
    currency                 varchar(3)     NOT NULL,
    cycle_length             integer        NOT NULL,
    commission_contributions integer        NOT NULL,
    start_date               date           NOT NULL,
    end_date                 date,
    target_amount            numeric(19, 4),
    status                   varchar(10)    NOT NULL,
    current_cycle            integer        NOT NULL DEFAULT 1,
    created_at               timestamptz    NOT NULL,
    created_by               uuid,
    closed_at                timestamptz,
    close_reason             varchar(300),
    version                  bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_susu_plan PRIMARY KEY (id),
    CONSTRAINT uq_susu_plan_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_susu_plan_number UNIQUE (tenant_id, plan_number),
    CONSTRAINT fk_susu_plan_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_susu_plan_account FOREIGN KEY (tenant_id, account_id) REFERENCES core.account (tenant_id, id),
    CONSTRAINT fk_susu_plan_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_susu_plan_frequency FOREIGN KEY (tenant_id, frequency_code)
        REFERENCES core.susu_frequency (tenant_id, code),
    CONSTRAINT ck_susu_plan_amounts CHECK (contribution_amount > 0 AND (target_amount IS NULL OR target_amount > 0)),
    CONSTRAINT ck_susu_plan_cycle CHECK (cycle_length BETWEEN 1 AND 366
        AND commission_contributions >= 0 AND commission_contributions < cycle_length AND current_cycle >= 1),
    CONSTRAINT ck_susu_plan_dates CHECK (end_date IS NULL OR end_date > start_date),
    CONSTRAINT ck_susu_plan_status CHECK (status IN ('ACTIVE', 'COMPLETED', 'CANCELLED')
        AND (status = 'ACTIVE') = (closed_at IS NULL))
);

-- One running plan per account.
CREATE UNIQUE INDEX uq_susu_plan_active_account ON core.susu_plan (tenant_id, account_id) WHERE status = 'ACTIVE';
CREATE INDEX ix_susu_plan_customer ON core.susu_plan (tenant_id, customer_id);
SELECT core.apply_tenant_isolation('core.susu_plan');
CREATE TRIGGER trg_susu_plan_no_delete
    BEFORE DELETE ON core.susu_plan
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.susu_plan TO ${app_db_role};
GRANT UPDATE (status, current_cycle, closed_at, close_reason, version) ON core.susu_plan TO ${app_db_role};

CREATE TABLE core.susu_contribution
(
    id                       uuid           NOT NULL,
    tenant_id                uuid           NOT NULL,
    plan_id                  uuid           NOT NULL,
    sequence_no              integer        NOT NULL,
    cycle_no                 integer        NOT NULL,
    due_date                 date           NOT NULL,
    amount                   numeric(19, 4) NOT NULL,
    status                   varchar(10)    NOT NULL,
    paid_at                  timestamptz,
    collection_id            uuid,
    financial_transaction_id uuid,
    waived_by                uuid,
    waive_reason             varchar(300),
    CONSTRAINT pk_susu_contribution PRIMARY KEY (id),
    CONSTRAINT uq_susu_contribution_sequence UNIQUE (plan_id, sequence_no),
    CONSTRAINT fk_susu_contribution_plan FOREIGN KEY (tenant_id, plan_id) REFERENCES core.susu_plan (tenant_id, id),
    CONSTRAINT fk_susu_contribution_collection FOREIGN KEY (tenant_id, collection_id)
        REFERENCES core.collection (tenant_id, id),
    CONSTRAINT fk_susu_contribution_transaction FOREIGN KEY (tenant_id, financial_transaction_id)
        REFERENCES core.financial_transaction (tenant_id, id),
    CONSTRAINT ck_susu_contribution_status CHECK (status IN ('EXPECTED', 'PAID', 'MISSED', 'WAIVED')),
    CONSTRAINT ck_susu_contribution_paid CHECK ((status = 'PAID') = (paid_at IS NOT NULL AND collection_id IS NOT NULL
        AND financial_transaction_id IS NOT NULL)),
    CONSTRAINT ck_susu_contribution_waived CHECK ((status = 'WAIVED') = (waive_reason IS NOT NULL))
);

CREATE INDEX ix_susu_contribution_unpaid ON core.susu_contribution (tenant_id, plan_id, sequence_no)
    WHERE status IN ('EXPECTED', 'MISSED');
CREATE INDEX ix_susu_contribution_due ON core.susu_contribution (tenant_id, due_date) WHERE status = 'EXPECTED';
SELECT core.apply_tenant_isolation('core.susu_contribution');

-- A contribution only moves forward: EXPECTED -> PAID / MISSED / WAIVED, MISSED -> PAID / WAIVED.
CREATE FUNCTION core.susu_contribution_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF (to_jsonb(NEW) - ARRAY ['status', 'paid_at', 'collection_id', 'financial_transaction_id', 'waived_by',
            'waive_reason']) <> (to_jsonb(OLD) - ARRAY ['status', 'paid_at', 'collection_id',
            'financial_transaction_id', 'waived_by', 'waive_reason'])
        OR OLD.status IN ('PAID', 'WAIVED')
        OR (OLD.status = 'MISSED' AND NEW.status NOT IN ('PAID', 'WAIVED')) THEN
        RAISE EXCEPTION 'A susu contribution cannot change from % to %', OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_susu_contribution_guard
    BEFORE UPDATE ON core.susu_contribution
    FOR EACH ROW EXECUTE FUNCTION core.susu_contribution_guard();
CREATE TRIGGER trg_susu_contribution_no_delete
    BEFORE DELETE ON core.susu_contribution
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.susu_contribution TO ${app_db_role};
GRANT UPDATE (status, paid_at, collection_id, financial_transaction_id, waived_by, waive_reason)
    ON core.susu_contribution TO ${app_db_role};

-- The commission of a finished cycle: due is N contributions; charged is what the account held, at most that.
CREATE TABLE core.susu_cycle_commission
(
    plan_id          uuid           NOT NULL,
    cycle_no         integer        NOT NULL,
    tenant_id        uuid           NOT NULL,
    amount_due       numeric(19, 4) NOT NULL,
    amount_charged   numeric(19, 4) NOT NULL,
    journal_entry_id uuid,
    business_date    date           NOT NULL,
    created_at       timestamptz    NOT NULL,
    CONSTRAINT pk_susu_cycle_commission PRIMARY KEY (plan_id, cycle_no),
    CONSTRAINT fk_susu_cycle_commission_plan FOREIGN KEY (tenant_id, plan_id)
        REFERENCES core.susu_plan (tenant_id, id),
    CONSTRAINT fk_susu_cycle_commission_journal FOREIGN KEY (tenant_id, journal_entry_id)
        REFERENCES core.journal_entry (tenant_id, id),
    CONSTRAINT ck_susu_cycle_commission_amounts CHECK (amount_due >= 0 AND amount_charged >= 0
        AND amount_charged <= amount_due AND (amount_charged > 0) = (journal_entry_id IS NOT NULL))
);

SELECT core.apply_tenant_isolation('core.susu_cycle_commission');
CREATE TRIGGER trg_susu_cycle_commission_immutable
    BEFORE UPDATE OR DELETE ON core.susu_cycle_commission
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.susu_cycle_commission TO ${app_db_role};
