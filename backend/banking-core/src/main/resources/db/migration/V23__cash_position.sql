-- =====================================================================================================
-- V23 Daily cash positions: end-of-day reconciliation of every vault and drawer.
--
-- For the closed business date, each cash point's ledger balance is set against the cash last counted in it (the
-- closing count of the drawer's last teller session). A drawer that received postings after it was counted shows
-- a BREAK. Vault counts are not recorded yet, so vaults show their position as NOT_COUNTED.
-- =====================================================================================================

CREATE TABLE core.cash_position
(
    tenant_id         uuid           NOT NULL,
    business_date     date           NOT NULL,
    cash_point_id     uuid           NOT NULL,
    cash_point_type   varchar(10)    NOT NULL,
    branch_id         uuid           NOT NULL,
    currency          varchar(3)     NOT NULL,
    ledger_balance    numeric(19, 4) NOT NULL,
    counted_balance   numeric(19, 4),
    teller_session_id uuid,
    counted_at        timestamptz,
    difference        numeric(19, 4),
    status            varchar(12)    NOT NULL,
    created_at        timestamptz    NOT NULL,
    CONSTRAINT pk_cash_position PRIMARY KEY (tenant_id, business_date, cash_point_id),
    CONSTRAINT fk_cash_position_branch FOREIGN KEY (tenant_id, branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_cash_position_session FOREIGN KEY (tenant_id, teller_session_id)
        REFERENCES core.teller_session (tenant_id, id),
    CONSTRAINT ck_cash_position_type CHECK (cash_point_type IN ('VAULT', 'DRAWER')),
    CONSTRAINT ck_cash_position_status CHECK (status IN ('MATCHED', 'BREAK', 'NOT_COUNTED')),
    CONSTRAINT ck_cash_position_count CHECK ((status = 'NOT_COUNTED') = (counted_balance IS NULL)
        AND (counted_balance IS NULL) = (difference IS NULL)
        AND (difference IS NULL OR difference = counted_balance - ledger_balance)
        AND (status <> 'MATCHED' OR difference = 0)
        AND (status <> 'BREAK' OR difference <> 0))
);

CREATE INDEX ix_cash_position_breaks ON core.cash_position (tenant_id, business_date) WHERE status = 'BREAK';
SELECT core.apply_tenant_isolation('core.cash_position');
CREATE TRIGGER trg_cash_position_immutable
    BEFORE UPDATE OR DELETE ON core.cash_position
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
GRANT SELECT, INSERT ON core.cash_position TO ${app_db_role};
