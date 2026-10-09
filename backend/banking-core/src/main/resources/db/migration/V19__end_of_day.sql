-- =====================================================================================================
-- V19 End-of-day processing and daily GL balance snapshots.
--
-- An end-of-day run closes one business date: it first moves the business date to the next working day (so
-- branches can carry on), then runs its steps for the closed date. Every step is idempotent and records its
-- progress, so a run that stops at any point (failure or crash) can be resumed and gives the same result.
-- =====================================================================================================

CREATE TABLE core.eod_run
(
    id                 uuid         NOT NULL,
    tenant_id          uuid         NOT NULL,
    business_date      date         NOT NULL,
    next_business_date date         NOT NULL,
    status             varchar(10)  NOT NULL,
    started_at         timestamptz  NOT NULL,
    started_by         uuid,
    finished_at        timestamptz,
    failed_step        varchar(40),
    failure_message    varchar(500),
    attempts           integer      NOT NULL DEFAULT 1,
    version            bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_eod_run PRIMARY KEY (id),
    CONSTRAINT uq_eod_run_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_eod_run_date UNIQUE (tenant_id, business_date),
    CONSTRAINT fk_eod_run_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_eod_run_status CHECK (status IN ('RUNNING', 'FAILED', 'COMPLETED')),
    CONSTRAINT ck_eod_run_dates CHECK (next_business_date > business_date),
    CONSTRAINT ck_eod_run_finished CHECK ((status = 'COMPLETED') = (finished_at IS NOT NULL))
);

-- At most one unfinished run per institution: the next day cannot start closing before the previous one closed.
CREATE UNIQUE INDEX uq_eod_run_unfinished ON core.eod_run (tenant_id) WHERE status <> 'COMPLETED';
SELECT core.apply_tenant_isolation('core.eod_run');
CREATE TRIGGER trg_eod_run_no_delete
    BEFORE DELETE ON core.eod_run
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.eod_run TO ${app_db_role};
GRANT UPDATE (status, finished_at, failed_step, failure_message, attempts, version) ON core.eod_run TO ${app_db_role};

CREATE TABLE core.eod_step
(
    eod_run_id  uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    step_code   varchar(40)  NOT NULL,
    step_order  integer      NOT NULL,
    status      varchar(10)  NOT NULL,
    result      jsonb,
    attempts    integer      NOT NULL DEFAULT 0,
    started_at  timestamptz,
    finished_at timestamptz,
    error       varchar(500),
    CONSTRAINT pk_eod_step PRIMARY KEY (eod_run_id, step_code),
    CONSTRAINT fk_eod_step_run FOREIGN KEY (tenant_id, eod_run_id) REFERENCES core.eod_run (tenant_id, id),
    CONSTRAINT ck_eod_step_status CHECK (status IN ('PENDING', 'RUNNING', 'DONE', 'FAILED'))
);

SELECT core.apply_tenant_isolation('core.eod_step');
CREATE TRIGGER trg_eod_step_no_delete
    BEFORE DELETE ON core.eod_step
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.eod_step TO ${app_db_role};
GRANT UPDATE (status, result, attempts, started_at, finished_at, error) ON core.eod_step TO ${app_db_role};

-- ------------------------------------------------------------------------------------- GL balance snapshots

-- Closing position of every GL account per branch and currency at the end of a business date. Balances are signed
-- debit-positive (debits minus credits); written once by end-of-day and never changed.
CREATE TABLE core.gl_balance_snapshot
(
    tenant_id           uuid           NOT NULL,
    business_date       date           NOT NULL,
    chart_of_account_id uuid           NOT NULL,
    branch_id           uuid           NOT NULL,
    currency            varchar(3)     NOT NULL,
    opening_balance     numeric(19, 4) NOT NULL,
    debits              numeric(19, 4) NOT NULL,
    credits             numeric(19, 4) NOT NULL,
    closing_balance     numeric(19, 4) NOT NULL,
    CONSTRAINT pk_gl_balance_snapshot PRIMARY KEY (tenant_id, business_date, chart_of_account_id, branch_id, currency),
    CONSTRAINT fk_gl_balance_snapshot_gl FOREIGN KEY (tenant_id, chart_of_account_id)
        REFERENCES core.chart_of_account (tenant_id, id),
    CONSTRAINT fk_gl_balance_snapshot_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT ck_gl_balance_snapshot_closing CHECK (closing_balance = opening_balance + debits - credits)
);

SELECT core.apply_tenant_isolation('core.gl_balance_snapshot');
CREATE TRIGGER trg_gl_balance_snapshot_immutable
    BEFORE UPDATE OR DELETE ON core.gl_balance_snapshot
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.gl_balance_snapshot TO ${app_db_role};
