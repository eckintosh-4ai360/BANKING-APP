-- =====================================================================================================
-- V29 Customer channel: digital banking credentials, trusted devices and one-time codes for customers.
--
-- A customer signs in with their phone number and password, on a device they trusted with a code texted to them;
-- money moves need their transaction PIN. Passwords and PINs are Argon2id hashes (the PIN is peppered with a key
-- held outside the database first: a PIN has too few values to survive an offline attack otherwise). One-time codes
-- are stored only as a keyed hash, expire within minutes and are good for one use.
-- =====================================================================================================

-- Customer sessions are bound to one of the customer's trusted devices.
ALTER TABLE core.auth_session DROP CONSTRAINT ck_auth_session_principal_type;
ALTER TABLE core.auth_session ADD CONSTRAINT ck_auth_session_principal_type
    CHECK (principal_type IN ('STAFF', 'PLATFORM', 'CUSTOMER'));
ALTER TABLE core.auth_session ADD COLUMN device_id uuid;
ALTER TABLE core.auth_session ADD CONSTRAINT ck_auth_session_device
    CHECK ((principal_type = 'CUSTOMER') = (device_id IS NOT NULL));
CREATE INDEX ix_auth_session_device ON core.auth_session (device_id) WHERE device_id IS NOT NULL;

CREATE TABLE core.customer_credential
(
    customer_id         uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    username            varchar(20)  NOT NULL,
    password_hash       varchar(255) NOT NULL,
    pin_hash            varchar(255) NOT NULL,
    status              varchar(10)  NOT NULL,
    disabled_reason     varchar(300),
    failed_attempts     integer      NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    pin_failed_attempts integer      NOT NULL DEFAULT 0,
    pin_locked          boolean      NOT NULL DEFAULT false,
    last_login_at       timestamptz,
    password_changed_at timestamptz  NOT NULL,
    pin_changed_at      timestamptz  NOT NULL,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_customer_credential PRIMARY KEY (customer_id),
    CONSTRAINT uq_customer_credential_tenant_customer UNIQUE (tenant_id, customer_id),
    CONSTRAINT uq_customer_credential_username UNIQUE (tenant_id, username),
    CONSTRAINT fk_customer_credential_customer FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer (tenant_id, id),
    CONSTRAINT ck_customer_credential_username CHECK (username ~ '^\+[1-9][0-9]{6,14}$'),
    CONSTRAINT ck_customer_credential_status CHECK (status IN ('ACTIVE', 'DISABLED')
        AND (status = 'DISABLED') = (disabled_reason IS NOT NULL)),
    CONSTRAINT ck_customer_credential_attempts CHECK (failed_attempts >= 0 AND pin_failed_attempts >= 0)
);

SELECT core.apply_tenant_isolation('core.customer_credential');
GRANT SELECT, INSERT, UPDATE ON core.customer_credential TO ${app_db_role};

CREATE TABLE core.customer_device
(
    id            uuid         NOT NULL,
    tenant_id     uuid         NOT NULL,
    customer_id   uuid         NOT NULL,
    device_key    varchar(100) NOT NULL,
    name          varchar(100) NOT NULL,
    platform      varchar(20),
    status        varchar(10)  NOT NULL,
    bound_at      timestamptz  NOT NULL,
    last_seen_at  timestamptz  NOT NULL,
    revoked_at    timestamptz,
    revoke_reason varchar(50),
    CONSTRAINT pk_customer_device PRIMARY KEY (id),
    CONSTRAINT uq_customer_device_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_customer_device_credential FOREIGN KEY (tenant_id, customer_id)
        REFERENCES core.customer_credential (tenant_id, customer_id),
    CONSTRAINT ck_customer_device_status CHECK (status IN ('ACTIVE', 'REVOKED')
        AND (status = 'REVOKED') = (revoked_at IS NOT NULL))
);

-- One trusted registration per installation and customer.
CREATE UNIQUE INDEX uq_customer_device_active ON core.customer_device (tenant_id, customer_id, device_key)
    WHERE status = 'ACTIVE';
SELECT core.apply_tenant_isolation('core.customer_device');
GRANT SELECT, INSERT ON core.customer_device TO ${app_db_role};
GRANT UPDATE (name, platform, status, last_seen_at, revoked_at, revoke_reason) ON core.customer_device
    TO ${app_db_role};

CREATE TABLE core.otp_challenge
(
    id           uuid         NOT NULL,
    tenant_id    uuid         NOT NULL,
    customer_id  uuid,
    phone        varchar(20)  NOT NULL,
    purpose      varchar(20)  NOT NULL,
    device_key   varchar(100),
    code_hash    varchar(64),
    attempts     integer      NOT NULL DEFAULT 0,
    max_attempts integer      NOT NULL,
    status       varchar(10)  NOT NULL,
    expires_at   timestamptz  NOT NULL,
    created_at   timestamptz  NOT NULL,
    verified_at  timestamptz,
    CONSTRAINT pk_otp_challenge PRIMARY KEY (id),
    CONSTRAINT fk_otp_challenge_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_otp_challenge_purpose CHECK (purpose IN ('ACTIVATION', 'DEVICE_BINDING', 'PASSWORD_RESET',
        'PIN_RESET')),
    CONSTRAINT ck_otp_challenge_status CHECK (status IN ('PENDING', 'VERIFIED', 'FAILED')
        AND (status = 'VERIFIED') = (verified_at IS NOT NULL)),
    CONSTRAINT ck_otp_challenge_attempts CHECK (attempts BETWEEN 0 AND max_attempts),
    -- A challenge that was never sent (the request matched no one) has no code, so it can never be answered.
    CONSTRAINT ck_otp_challenge_code CHECK (code_hash IS NOT NULL OR customer_id IS NULL)
);

CREATE INDEX ix_otp_challenge_phone ON core.otp_challenge (tenant_id, phone, created_at);
SELECT core.apply_tenant_isolation('core.otp_challenge');
GRANT SELECT, INSERT ON core.otp_challenge TO ${app_db_role};
GRANT UPDATE (attempts, status, verified_at) ON core.otp_challenge TO ${app_db_role};
