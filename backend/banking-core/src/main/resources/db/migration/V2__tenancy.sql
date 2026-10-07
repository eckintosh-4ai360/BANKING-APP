-- =====================================================================================================
-- V2 Tenancy: tenant registry, institution profile (branding/contact), feature catalog and licensing.
-- =====================================================================================================

CREATE TABLE core.tenant
(
    id               uuid         NOT NULL,
    code             varchar(32)  NOT NULL,
    legal_name       varchar(200) NOT NULL,
    display_name     varchar(120) NOT NULL,
    institution_type varchar(30)  NOT NULL,
    status           varchar(20)  NOT NULL,
    country_code     varchar(2)   NOT NULL,
    base_currency    varchar(3)   NOT NULL,
    timezone         varchar(64)  NOT NULL,
    locale           varchar(20)  NOT NULL,
    licence_number   varchar(64),
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    created_by       uuid,
    updated_by       uuid,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant PRIMARY KEY (id),
    CONSTRAINT uq_tenant_code UNIQUE (code),
    CONSTRAINT ck_tenant_code CHECK (code ~ '^[a-z0-9][a-z0-9-]{1,31}$'),
    CONSTRAINT ck_tenant_institution_type CHECK (institution_type IN
                                                 ('MICROFINANCE', 'SAVINGS_AND_LOANS', 'CREDIT_UNION',
                                                  'RURAL_COMMUNITY_BANK', 'SUSU_OPERATOR', 'DIGITAL_LENDER',
                                                  'COOPERATIVE', 'OTHER')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('ONBOARDING', 'ACTIVE', 'SUSPENDED', 'TERMINATED')),
    CONSTRAINT ck_tenant_country_code CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_tenant_base_currency CHECK (base_currency ~ '^[A-Z]{3}$')
);

-- The registry is visible in platform context (no tenant set) and, inside a tenant context, only the own row.
ALTER TABLE core.tenant ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_registry_access ON core.tenant
    USING (core.current_tenant_id() IS NULL OR id = core.current_tenant_id())
    WITH CHECK (core.current_tenant_id() IS NULL OR id = core.current_tenant_id());

GRANT SELECT, INSERT, UPDATE ON core.tenant TO ${app_db_role};


CREATE TABLE core.institution_profile
(
    tenant_id            uuid        NOT NULL,
    contact_email        varchar(254),
    contact_phone        varchar(20),
    support_email        varchar(254),
    support_phone        varchar(20),
    website_url          varchar(255),
    address_line1        varchar(200),
    address_line2        varchar(200),
    city                 varchar(100),
    region               varchar(100),
    digital_address      varchar(20),
    logo_url             varchar(500),
    primary_color        varchar(7)  NOT NULL,
    secondary_color      varchar(7)  NOT NULL,
    sms_sender_id        varchar(11),
    email_sender_name    varchar(100),
    email_sender_address varchar(254),
    created_at           timestamptz NOT NULL,
    updated_at           timestamptz NOT NULL,
    created_by           uuid,
    updated_by           uuid,
    version              bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_institution_profile PRIMARY KEY (tenant_id),
    CONSTRAINT fk_institution_profile_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_institution_profile_primary_color CHECK (primary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_institution_profile_secondary_color CHECK (secondary_color ~ '^#[0-9A-Fa-f]{6}$'),
    CONSTRAINT ck_institution_profile_sms_sender CHECK (sms_sender_id ~ '^[A-Za-z0-9][A-Za-z0-9 ]{0,10}$')
);

SELECT core.apply_tenant_isolation('core.institution_profile');
GRANT SELECT, INSERT, UPDATE ON core.institution_profile TO ${app_db_role};


-- Platform-wide catalog of licensable features.
CREATE TABLE core.feature
(
    code        varchar(40)  NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255) NOT NULL,
    CONSTRAINT pk_feature PRIMARY KEY (code),
    CONSTRAINT ck_feature_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,39}$')
);

INSERT INTO core.feature (code, name, description)
VALUES ('SAVINGS', 'Savings', 'Savings and current deposit accounts'),
       ('LOANS', 'Loans', 'Loan products, origination, servicing and collections'),
       ('SUSU', 'Susu', 'Susu contribution plans and collections'),
       ('FIXED_DEPOSIT', 'Fixed Deposit', 'Term deposits with maturity handling'),
       ('TARGET_SAVINGS', 'Target Savings', 'Goal-based savings plans'),
       ('MOBILE_MONEY', 'Mobile Money', 'Wallet-to-account and account-to-wallet transfers'),
       ('INTERBANK_TRANSFERS', 'Interbank Transfers', 'Transfers to and from other financial institutions'),
       ('FIELD_COLLECTIONS', 'Field Collections', 'Field officer collections, visits and offline sync'),
       ('GROUP_LENDING', 'Group Lending', 'Group and joint-liability lending'),
       ('USSD', 'USSD', 'USSD self-service channel'),
       ('CUSTOMER_MOBILE_APP', 'Customer Mobile App', 'Customer self-service mobile banking'),
       ('AI_CREDIT_ASSESSMENT', 'AI Credit Assessment', 'Advisory AI credit scoring for loan officers');

GRANT SELECT ON core.feature TO ${app_db_role};


CREATE TABLE core.tenant_feature
(
    tenant_id    uuid        NOT NULL,
    feature_code varchar(40) NOT NULL,
    licensed     boolean     NOT NULL,
    enabled      boolean     NOT NULL,
    updated_at   timestamptz NOT NULL,
    updated_by   uuid,
    version      bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_tenant_feature PRIMARY KEY (tenant_id, feature_code),
    CONSTRAINT fk_tenant_feature_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_tenant_feature_feature FOREIGN KEY (feature_code) REFERENCES core.feature (code),
    -- A tenant can never switch on a feature the platform has not licensed to it.
    CONSTRAINT ck_tenant_feature_licence CHECK (licensed OR NOT enabled)
);

SELECT core.apply_tenant_isolation('core.tenant_feature');
GRANT SELECT, INSERT, UPDATE ON core.tenant_feature TO ${app_db_role};
