-- =====================================================================================================
-- V7 Institution-configurable KYC reference data: accepted identification types and KYC tiers.
-- Defaults are provisioned per institution by the application (country-aware), not hard-coded here.
-- =====================================================================================================

CREATE TABLE core.identification_type
(
    tenant_id                        uuid         NOT NULL,
    code                             varchar(30)  NOT NULL,
    name                             varchar(100) NOT NULL,
    applies_to                       varchar(20)  NOT NULL,
    format_regex                     varchar(200),
    format_hint                      varchar(100),
    requires_expiry                  boolean      NOT NULL,
    supports_electronic_verification boolean      NOT NULL,
    active                           boolean      NOT NULL,
    sort_order                       integer      NOT NULL DEFAULT 0,
    created_at                       timestamptz  NOT NULL,
    updated_at                       timestamptz  NOT NULL,
    created_by                       uuid,
    updated_by                       uuid,
    version                          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_identification_type PRIMARY KEY (tenant_id, code),
    CONSTRAINT fk_identification_type_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_identification_type_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ck_identification_type_applies_to CHECK (applies_to IN ('INDIVIDUAL', 'BUSINESS', 'ANY'))
);

SELECT core.apply_tenant_isolation('core.identification_type');
GRANT SELECT, INSERT, UPDATE ON core.identification_type TO ${app_db_role};


CREATE TABLE core.kyc_tier
(
    tenant_id                      uuid         NOT NULL,
    code                           varchar(30)  NOT NULL,
    name                           varchar(100) NOT NULL,
    description                    varchar(255),
    tier_rank                      integer      NOT NULL,
    requires_identification        boolean      NOT NULL,
    requires_id_document           boolean      NOT NULL,
    requires_selfie                boolean      NOT NULL,
    requires_address               boolean      NOT NULL,
    requires_proof_of_address      boolean      NOT NULL,
    requires_identity_verification boolean      NOT NULL,
    requires_next_of_kin           boolean      NOT NULL,
    requires_signature             boolean      NOT NULL,
    requires_employment_info       boolean      NOT NULL,
    active                         boolean      NOT NULL,
    created_at                     timestamptz  NOT NULL,
    updated_at                     timestamptz  NOT NULL,
    created_by                     uuid,
    updated_by                     uuid,
    version                        bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_kyc_tier PRIMARY KEY (tenant_id, code),
    CONSTRAINT uq_kyc_tier_rank UNIQUE (tenant_id, tier_rank),
    CONSTRAINT fk_kyc_tier_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_kyc_tier_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,29}$'),
    CONSTRAINT ck_kyc_tier_rank CHECK (tier_rank BETWEEN 1 AND 10)
);

SELECT core.apply_tenant_isolation('core.kyc_tier');
GRANT SELECT, INSERT, UPDATE ON core.kyc_tier TO ${app_db_role};
