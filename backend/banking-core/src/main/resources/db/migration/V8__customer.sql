-- =====================================================================================================
-- V8 Customer master data: customers (individual / business / group), profiles, addresses, identifications,
-- next of kin and related parties (directors, owners, signatories).
--
-- National ID and tax numbers are stored only encrypted (AES-GCM, application-level) with an HMAC blind index
-- for duplicate detection and a masked copy for display.
-- =====================================================================================================

-- Trigram indexes for fast partial-name and phone search. pg_trgm is a trusted extension, so the schema owner
-- may create it.
CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA core;

CREATE TABLE core.customer
(
    id                      uuid         NOT NULL,
    tenant_id               uuid         NOT NULL,
    customer_number         varchar(20)  NOT NULL,
    customer_type           varchar(20)  NOT NULL,
    status                  varchar(20)  NOT NULL,
    status_reason           varchar(255),
    kyc_status              varchar(20)  NOT NULL,
    kyc_tier_code           varchar(30),
    kyc_verified_at         timestamptz,
    risk_level              varchar(20)  NOT NULL,
    display_name            varchar(200) NOT NULL,
    primary_phone           varchar(20),
    email                   varchar(254),
    home_branch_id          uuid         NOT NULL,
    relationship_officer_id uuid,
    onboarding_channel      varchar(20)  NOT NULL,
    preferred_language      varchar(10),
    profile_updated_at      timestamptz  NOT NULL,
    created_at              timestamptz  NOT NULL,
    updated_at              timestamptz  NOT NULL,
    created_by              uuid,
    updated_by              uuid,
    version                 bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer PRIMARY KEY (id),
    CONSTRAINT uq_customer_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_customer_tenant_number UNIQUE (tenant_id, customer_number),
    CONSTRAINT fk_customer_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_customer_home_branch FOREIGN KEY (tenant_id, home_branch_id)
        REFERENCES core.branch (tenant_id, id),
    CONSTRAINT fk_customer_relationship_officer FOREIGN KEY (tenant_id, relationship_officer_id)
        REFERENCES core.staff (tenant_id, id),
    CONSTRAINT fk_customer_kyc_tier FOREIGN KEY (tenant_id, kyc_tier_code)
        REFERENCES core.kyc_tier (tenant_id, code),
    CONSTRAINT ck_customer_type CHECK (customer_type IN ('INDIVIDUAL', 'BUSINESS', 'GROUP')),
    CONSTRAINT ck_customer_status CHECK (status IN
                                         ('PENDING', 'ACTIVE', 'DORMANT', 'RESTRICTED', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_customer_kyc_status CHECK (kyc_status IN
                                             ('NOT_STARTED', 'IN_PROGRESS', 'PENDING_REVIEW', 'VERIFIED',
                                              'REJECTED', 'EXPIRED')),
    CONSTRAINT ck_customer_risk_level CHECK (risk_level IN ('UNASSESSED', 'LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT ck_customer_channel CHECK (onboarding_channel IN ('BRANCH', 'FIELD', 'MOBILE', 'WEB', 'API')),
    -- A verified customer always has a tier; only a once-verified customer can be active.
    CONSTRAINT ck_customer_verified_tier CHECK (kyc_status <> 'VERIFIED' OR kyc_tier_code IS NOT NULL),
    CONSTRAINT ck_customer_active_verified CHECK (status <> 'ACTIVE' OR kyc_verified_at IS NOT NULL)
);

CREATE INDEX ix_customer_tenant_branch_status ON core.customer (tenant_id, home_branch_id, status);
CREATE INDEX ix_customer_tenant_kyc_status ON core.customer (tenant_id, kyc_status);
CREATE INDEX ix_customer_tenant_email ON core.customer (tenant_id, lower(email));
CREATE INDEX ix_customer_name_trgm ON core.customer USING gin (lower(display_name) core.gin_trgm_ops);
CREATE INDEX ix_customer_phone_trgm ON core.customer USING gin (primary_phone core.gin_trgm_ops);

SELECT core.apply_tenant_isolation('core.customer');
GRANT SELECT, INSERT, UPDATE ON core.customer TO ${app_db_role};


CREATE TABLE core.individual_profile
(
    customer_id         uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    title               varchar(20),
    first_name          varchar(100) NOT NULL,
    middle_name         varchar(100),
    last_name           varchar(100) NOT NULL,
    date_of_birth       date         NOT NULL,
    gender              varchar(20),
    nationality         varchar(2),
    marital_status      varchar(20),
    occupation          varchar(100),
    employer_name       varchar(150),
    employment_status   varchar(20),
    monthly_income_band varchar(30),
    tax_id_encrypted    varchar(512),
    tax_id_blind_index  varchar(64),
    tax_id_masked       varchar(40),
    CONSTRAINT pk_individual_profile PRIMARY KEY (customer_id),
    CONSTRAINT fk_individual_profile_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_individual_profile_gender CHECK (gender IN ('MALE', 'FEMALE', 'OTHER', 'UNDISCLOSED')),
    CONSTRAINT ck_individual_profile_marital CHECK (marital_status IN
                                                    ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'SEPARATED',
                                                     'UNDISCLOSED')),
    CONSTRAINT ck_individual_profile_employment CHECK (employment_status IN
                                                       ('EMPLOYED', 'SELF_EMPLOYED', 'UNEMPLOYED', 'STUDENT',
                                                        'RETIRED', 'OTHER')),
    CONSTRAINT ck_individual_profile_nationality CHECK (nationality ~ '^[A-Z]{2}$'),
    CONSTRAINT ck_individual_profile_dob CHECK (date_of_birth > DATE '1900-01-01'),
    CONSTRAINT ck_individual_profile_tax_id CHECK ((tax_id_encrypted IS NULL) = (tax_id_blind_index IS NULL))
);

CREATE INDEX ix_individual_profile_tax_id ON core.individual_profile (tenant_id, tax_id_blind_index);

SELECT core.apply_tenant_isolation('core.individual_profile');
GRANT SELECT, INSERT, UPDATE ON core.individual_profile TO ${app_db_role};


CREATE TABLE core.business_profile
(
    customer_id         uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    registered_name     varchar(200) NOT NULL,
    trading_name        varchar(200),
    registration_number varchar(50)  NOT NULL,
    registration_date   date,
    business_type       varchar(30)  NOT NULL,
    industry_sector     varchar(100),
    annual_turnover_band varchar(30),
    number_of_employees integer,
    tax_id_encrypted    varchar(512),
    tax_id_blind_index  varchar(64),
    tax_id_masked       varchar(40),
    CONSTRAINT pk_business_profile PRIMARY KEY (customer_id),
    CONSTRAINT fk_business_profile_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_business_profile_type CHECK (business_type IN
                                               ('SOLE_PROPRIETORSHIP', 'PARTNERSHIP', 'LIMITED_COMPANY',
                                                'COOPERATIVE', 'NGO', 'ASSOCIATION', 'OTHER')),
    CONSTRAINT ck_business_profile_employees CHECK (number_of_employees IS NULL OR number_of_employees >= 0),
    CONSTRAINT ck_business_profile_tax_id CHECK ((tax_id_encrypted IS NULL) = (tax_id_blind_index IS NULL))
);

CREATE UNIQUE INDEX uq_business_profile_registration ON core.business_profile (tenant_id, upper(registration_number));

SELECT core.apply_tenant_isolation('core.business_profile');
GRANT SELECT, INSERT, UPDATE ON core.business_profile TO ${app_db_role};


CREATE TABLE core.customer_address
(
    id              uuid         NOT NULL,
    tenant_id       uuid         NOT NULL,
    customer_id     uuid         NOT NULL,
    address_type    varchar(20)  NOT NULL,
    line1           varchar(200) NOT NULL,
    line2           varchar(200),
    city            varchar(100),
    district        varchar(100),
    region          varchar(100),
    country_code    varchar(2)   NOT NULL,
    digital_address varchar(20),
    landmark        varchar(200),
    is_primary      boolean      NOT NULL,
    active          boolean      NOT NULL,
    verified_at     timestamptz,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    created_by      uuid,
    updated_by      uuid,
    version         bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_address PRIMARY KEY (id),
    CONSTRAINT fk_customer_address_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_customer_address_type CHECK (address_type IN ('RESIDENTIAL', 'BUSINESS', 'MAILING', 'PERMANENT')),
    CONSTRAINT ck_customer_address_country CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE INDEX ix_customer_address_customer ON core.customer_address (tenant_id, customer_id);
CREATE UNIQUE INDEX uq_customer_address_primary ON core.customer_address (customer_id) WHERE is_primary AND active;

SELECT core.apply_tenant_isolation('core.customer_address');
GRANT SELECT, INSERT, UPDATE ON core.customer_address TO ${app_db_role};


CREATE TABLE core.customer_identification
(
    id                     uuid        NOT NULL,
    tenant_id              uuid        NOT NULL,
    customer_id            uuid        NOT NULL,
    id_type_code           varchar(30) NOT NULL,
    id_number_encrypted    varchar(512) NOT NULL,
    id_number_blind_index  varchar(64) NOT NULL,
    id_number_masked       varchar(40) NOT NULL,
    issuing_country        varchar(2),
    issue_date             date,
    expiry_date            date,
    is_primary             boolean     NOT NULL,
    active                 boolean     NOT NULL,
    verification_status    varchar(20) NOT NULL,
    verified_at            timestamptz,
    verification_reference varchar(100),
    created_at             timestamptz NOT NULL,
    updated_at             timestamptz NOT NULL,
    created_by             uuid,
    updated_by             uuid,
    version                bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_identification PRIMARY KEY (id),
    CONSTRAINT fk_customer_identification_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_identification_type FOREIGN KEY (tenant_id, id_type_code)
        REFERENCES core.identification_type (tenant_id, code),
    CONSTRAINT ck_customer_identification_status CHECK (verification_status IN ('UNVERIFIED', 'VERIFIED', 'FAILED')),
    CONSTRAINT ck_customer_identification_dates CHECK (expiry_date IS NULL OR issue_date IS NULL
        OR expiry_date > issue_date),
    CONSTRAINT ck_customer_identification_country CHECK (issuing_country ~ '^[A-Z]{2}$')
);

CREATE INDEX ix_customer_identification_customer ON core.customer_identification (tenant_id, customer_id);
-- The same identity document can belong to only one active customer record per institution.
CREATE UNIQUE INDEX uq_customer_identification_number ON core.customer_identification
    (tenant_id, id_type_code, id_number_blind_index) WHERE active;
CREATE UNIQUE INDEX uq_customer_identification_primary ON core.customer_identification (customer_id)
    WHERE is_primary AND active;

SELECT core.apply_tenant_isolation('core.customer_identification');
GRANT SELECT, INSERT, UPDATE ON core.customer_identification TO ${app_db_role};


CREATE TABLE core.customer_next_of_kin
(
    id           uuid         NOT NULL,
    tenant_id    uuid         NOT NULL,
    customer_id  uuid         NOT NULL,
    full_name    varchar(200) NOT NULL,
    relationship varchar(50)  NOT NULL,
    phone        varchar(20),
    email        varchar(254),
    address      varchar(300),
    is_primary   boolean      NOT NULL,
    active       boolean      NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    created_by   uuid,
    updated_by   uuid,
    version      bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_next_of_kin PRIMARY KEY (id),
    CONSTRAINT fk_customer_next_of_kin_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id)
);

CREATE INDEX ix_customer_next_of_kin_customer ON core.customer_next_of_kin (tenant_id, customer_id);

SELECT core.apply_tenant_isolation('core.customer_next_of_kin');
GRANT SELECT, INSERT, UPDATE ON core.customer_next_of_kin TO ${app_db_role};


CREATE TABLE core.customer_related_party
(
    id                    uuid         NOT NULL,
    tenant_id             uuid         NOT NULL,
    customer_id           uuid         NOT NULL,
    related_customer_id   uuid,
    full_name             varchar(200) NOT NULL,
    party_role            varchar(30)  NOT NULL,
    ownership_percent     numeric(5, 2),
    nationality           varchar(2),
    date_of_birth         date,
    phone                 varchar(20),
    email                 varchar(254),
    id_type_code          varchar(30),
    id_number_encrypted   varchar(512),
    id_number_blind_index varchar(64),
    id_number_masked      varchar(40),
    politically_exposed   boolean      NOT NULL,
    active                boolean      NOT NULL,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    created_by            uuid,
    updated_by            uuid,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_related_party PRIMARY KEY (id),
    CONSTRAINT fk_customer_related_party_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_related_party_related FOREIGN KEY (tenant_id, related_customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT fk_customer_related_party_id_type FOREIGN KEY (tenant_id, id_type_code)
        REFERENCES core.identification_type (tenant_id, code),
    CONSTRAINT ck_customer_related_party_role CHECK (party_role IN
                                                     ('DIRECTOR', 'SHAREHOLDER', 'BENEFICIAL_OWNER',
                                                      'AUTHORISED_SIGNATORY', 'PARTNER', 'TRUSTEE')),
    CONSTRAINT ck_customer_related_party_ownership CHECK (ownership_percent IS NULL
        OR (ownership_percent > 0 AND ownership_percent <= 100)),
    CONSTRAINT ck_customer_related_party_id CHECK ((id_number_encrypted IS NULL) = (id_type_code IS NULL))
);

CREATE INDEX ix_customer_related_party_customer ON core.customer_related_party (tenant_id, customer_id);

SELECT core.apply_tenant_isolation('core.customer_related_party');
GRANT SELECT, INSERT, UPDATE ON core.customer_related_party TO ${app_db_role};
