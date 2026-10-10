-- =====================================================================================================
-- V32 Self-onboarding: people who are not yet customers sign up in the app.
--
-- They verify their phone, register (becoming a PENDING customer at the institution's onboarding branch), then
-- capture their details and documents step by step and submit them for KYC. Staff review the case as for any
-- onboarding; once approved the customer opens their first account in the app. Whether the app offers sign-up,
-- and with which branch, KYC tier and product, is the institution's setting.
-- =====================================================================================================

ALTER TABLE core.otp_challenge DROP CONSTRAINT ck_otp_challenge_purpose;
ALTER TABLE core.otp_challenge ADD CONSTRAINT ck_otp_challenge_purpose CHECK (purpose IN ('ACTIVATION',
    'DEVICE_BINDING', 'PASSWORD_RESET', 'PIN_RESET', 'REGISTRATION'));

ALTER TABLE core.customer_channel_settings
    ADD COLUMN self_onboarding_enabled boolean     NOT NULL DEFAULT false,
    ADD COLUMN onboarding_branch_id    uuid,
    ADD COLUMN onboarding_tier_code    varchar(30),
    ADD COLUMN onboarding_product_id   uuid,
    ADD COLUMN onboarding_min_age      integer     NOT NULL DEFAULT 18,
    ADD CONSTRAINT fk_customer_channel_settings_branch FOREIGN KEY (tenant_id, onboarding_branch_id)
        REFERENCES core.branch (tenant_id, id),
    ADD CONSTRAINT fk_customer_channel_settings_tier FOREIGN KEY (tenant_id, onboarding_tier_code)
        REFERENCES core.kyc_tier (tenant_id, code),
    ADD CONSTRAINT fk_customer_channel_settings_product FOREIGN KEY (tenant_id, onboarding_product_id)
        REFERENCES core.account_product (tenant_id, id),
    ADD CONSTRAINT ck_customer_channel_settings_onboarding CHECK (NOT self_onboarding_enabled
        OR (onboarding_branch_id IS NOT NULL AND onboarding_tier_code IS NOT NULL
            AND onboarding_product_id IS NOT NULL)),
    ADD CONSTRAINT ck_customer_channel_settings_min_age CHECK (onboarding_min_age BETWEEN 0 AND 120);

GRANT UPDATE (self_onboarding_enabled, onboarding_branch_id, onboarding_tier_code, onboarding_product_id,
    onboarding_min_age) ON core.customer_channel_settings TO ${app_db_role};

-- A customer's own declaration is evidence the reviewer sees with the case (a declared politically exposed person
-- must be rated high risk, as for a screening match).
ALTER TABLE core.kyc_check DROP CONSTRAINT ck_kyc_check_method;
ALTER TABLE core.kyc_check ADD CONSTRAINT ck_kyc_check_method CHECK (method IN ('ELECTRONIC', 'MANUAL',
    'DECLARED'));

-- One per customer who signed up in the app: their risk profile answers and how far they have come.
CREATE TABLE core.customer_onboarding
(
    customer_id               uuid         NOT NULL,
    tenant_id                 uuid         NOT NULL,
    source_of_funds           varchar(30),
    account_purpose           varchar(30),
    expected_monthly_turnover varchar(30),
    politically_exposed       boolean,
    risk_answered_at          timestamptz,
    kyc_case_id               uuid,
    submitted_at              timestamptz,
    account_id                uuid,
    completed_at              timestamptz,
    created_at                timestamptz  NOT NULL,
    updated_at                timestamptz  NOT NULL,
    version                   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_onboarding PRIMARY KEY (customer_id),
    CONSTRAINT fk_customer_onboarding_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_onboarding_case FOREIGN KEY (tenant_id, kyc_case_id)
        REFERENCES core.kyc_case (tenant_id, id),
    CONSTRAINT fk_customer_onboarding_account FOREIGN KEY (tenant_id, account_id)
        REFERENCES core.account (tenant_id, id),
    CONSTRAINT ck_customer_onboarding_source CHECK (source_of_funds IS NULL OR source_of_funds IN ('SALARY',
        'BUSINESS', 'FARMING', 'TRADING', 'REMITTANCES', 'PENSION', 'FAMILY_SUPPORT', 'SAVINGS', 'OTHER')),
    CONSTRAINT ck_customer_onboarding_purpose CHECK (account_purpose IS NULL OR account_purpose IN ('SAVINGS',
        'RECEIVING_SALARY', 'BUSINESS_PAYMENTS', 'SUSU', 'LOANS', 'REMITTANCES', 'OTHER')),
    CONSTRAINT ck_customer_onboarding_turnover CHECK (expected_monthly_turnover IS NULL
        OR expected_monthly_turnover IN ('UP_TO_1000', 'UP_TO_5000', 'UP_TO_20000', 'UP_TO_100000',
                                         'ABOVE_100000')),
    CONSTRAINT ck_customer_onboarding_risk CHECK ((risk_answered_at IS NULL) = (politically_exposed IS NULL)
        AND (risk_answered_at IS NULL OR (source_of_funds IS NOT NULL AND account_purpose IS NOT NULL
            AND expected_monthly_turnover IS NOT NULL))),
    CONSTRAINT ck_customer_onboarding_completed CHECK ((completed_at IS NULL) = (account_id IS NULL))
);

CREATE INDEX ix_customer_onboarding_case ON core.customer_onboarding (tenant_id, kyc_case_id);

SELECT core.apply_tenant_isolation('core.customer_onboarding');
GRANT SELECT, INSERT ON core.customer_onboarding TO ${app_db_role};
GRANT UPDATE (source_of_funds, account_purpose, expected_monthly_turnover, politically_exposed, risk_answered_at,
    kyc_case_id, submitted_at, account_id, completed_at, updated_at, version)
    ON core.customer_onboarding TO ${app_db_role};
