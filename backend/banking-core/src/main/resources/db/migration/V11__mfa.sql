-- =====================================================================================================
-- V11 TOTP multi-factor authentication for staff and platform administrators.
-- The TOTP secret is stored encrypted (AES-GCM, application-level). mfa_last_used_step blocks code replay.
-- =====================================================================================================

ALTER TABLE core.staff_credential
    ADD COLUMN mfa_secret_encrypted varchar(512),
    ADD COLUMN mfa_enabled          boolean NOT NULL DEFAULT false,
    ADD COLUMN mfa_enrolled_at      timestamptz,
    ADD COLUMN mfa_last_used_step   bigint,
    ADD CONSTRAINT ck_staff_credential_mfa CHECK (NOT mfa_enabled OR mfa_secret_encrypted IS NOT NULL);

ALTER TABLE core.platform_user
    ADD COLUMN mfa_secret_encrypted varchar(512),
    ADD COLUMN mfa_enabled          boolean NOT NULL DEFAULT false,
    ADD COLUMN mfa_enrolled_at      timestamptz,
    ADD COLUMN mfa_last_used_step   bigint,
    ADD CONSTRAINT ck_platform_user_mfa CHECK (NOT mfa_enabled OR mfa_secret_encrypted IS NOT NULL);
