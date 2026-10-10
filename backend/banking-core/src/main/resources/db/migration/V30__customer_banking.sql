-- =====================================================================================================
-- V30 Customer banking: the institution's limits for the customer app, and customers' saved beneficiaries.
--
-- App transfers are capped per transfer and per day, and more tightly for a while after a beneficiary is added or a
-- new device is trusted (a takeover usually adds both before emptying the accounts). The limits are the
-- institution's settings, in its base currency.
-- =====================================================================================================

CREATE TABLE core.customer_channel_settings
(
    tenant_id                  uuid           NOT NULL,
    max_transfer_amount        numeric(19, 4) NOT NULL,
    daily_transfer_limit       numeric(19, 4) NOT NULL,
    beneficiary_cooldown_hours integer        NOT NULL,
    cooldown_max_amount        numeric(19, 4) NOT NULL,
    new_device_cooldown_hours  integer        NOT NULL,
    new_device_max_amount      numeric(19, 4) NOT NULL,
    updated_at                 timestamptz    NOT NULL,
    updated_by                 uuid,
    version                    bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_channel_settings PRIMARY KEY (tenant_id),
    CONSTRAINT fk_customer_channel_settings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_customer_channel_settings_amounts CHECK (max_transfer_amount > 0
        AND daily_transfer_limit >= max_transfer_amount AND cooldown_max_amount > 0
        AND cooldown_max_amount <= max_transfer_amount AND new_device_max_amount > 0
        AND new_device_max_amount <= max_transfer_amount),
    CONSTRAINT ck_customer_channel_settings_hours CHECK (beneficiary_cooldown_hours BETWEEN 0 AND 720
        AND new_device_cooldown_hours BETWEEN 0 AND 720)
);

SELECT core.apply_tenant_isolation('core.customer_channel_settings');
GRANT SELECT, INSERT ON core.customer_channel_settings TO ${app_db_role};
GRANT UPDATE (max_transfer_amount, daily_transfer_limit, beneficiary_cooldown_hours, cooldown_max_amount,
    new_device_cooldown_hours, new_device_max_amount, updated_at, updated_by, version)
    ON core.customer_channel_settings TO ${app_db_role};

-- A saved destination. Only accounts of the institution (INTERNAL) can be paid until the payment integrations
-- (bank transfers and mobile money) arrive.
CREATE TABLE core.beneficiary
(
    id                uuid           NOT NULL,
    tenant_id         uuid           NOT NULL,
    customer_id       uuid           NOT NULL,
    beneficiary_type  varchar(15)    NOT NULL,
    nickname          varchar(60)    NOT NULL,
    account_id        uuid,
    display_name      varchar(100)   NOT NULL,
    favourite         boolean        NOT NULL DEFAULT false,
    transfer_limit    numeric(19, 4),
    status            varchar(10)    NOT NULL,
    cooldown_until    timestamptz    NOT NULL,
    created_at        timestamptz    NOT NULL,
    removed_at        timestamptz,
    version           bigint         NOT NULL DEFAULT 0,
    CONSTRAINT pk_beneficiary PRIMARY KEY (id),
    CONSTRAINT uq_beneficiary_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_beneficiary_customer FOREIGN KEY (tenant_id, customer_id) REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_beneficiary_account FOREIGN KEY (tenant_id, account_id) REFERENCES core.account (tenant_id, id),
    CONSTRAINT ck_beneficiary_type CHECK (beneficiary_type IN ('INTERNAL', 'BANK', 'MOBILE_MONEY')
        AND (beneficiary_type = 'INTERNAL') = (account_id IS NOT NULL)),
    CONSTRAINT ck_beneficiary_status CHECK (status IN ('ACTIVE', 'REMOVED')
        AND (status = 'REMOVED') = (removed_at IS NOT NULL)),
    CONSTRAINT ck_beneficiary_limit CHECK (transfer_limit IS NULL OR transfer_limit > 0)
);

CREATE UNIQUE INDEX uq_beneficiary_active_account ON core.beneficiary (tenant_id, customer_id, account_id)
    WHERE status = 'ACTIVE' AND account_id IS NOT NULL;
CREATE INDEX ix_beneficiary_customer ON core.beneficiary (tenant_id, customer_id, status);
SELECT core.apply_tenant_isolation('core.beneficiary');
GRANT SELECT, INSERT ON core.beneficiary TO ${app_db_role};
GRANT UPDATE (nickname, favourite, transfer_limit, status, removed_at, version) ON core.beneficiary TO ${app_db_role};

-- What a customer sent from the app today is summed from their transactions.
CREATE INDEX ix_financial_transaction_mobile ON core.financial_transaction (tenant_id, initiated_by, business_date)
    WHERE channel = 'MOBILE';
