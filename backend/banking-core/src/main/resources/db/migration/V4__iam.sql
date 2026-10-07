-- =====================================================================================================
-- V4 Identity & access: permission catalog, roles, staff, staff credentials, platform users, sessions.
-- =====================================================================================================

-- ---------------------------------------------------------------------------------------------------
-- Permission catalog (global). Must match com.company.banking.common.security.Permissions
-- (verified by PermissionCatalogIT).
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE core.permission
(
    code        varchar(64)  NOT NULL,
    module      varchar(40)  NOT NULL,
    description varchar(255) NOT NULL,
    scope       varchar(10)  NOT NULL,
    sensitive   boolean      NOT NULL DEFAULT false,
    CONSTRAINT pk_permission PRIMARY KEY (code),
    CONSTRAINT ck_permission_code CHECK (code ~ '^[a-z]+(\.[a-z_]+)+$'),
    CONSTRAINT ck_permission_scope CHECK (scope IN ('TENANT', 'PLATFORM'))
);

INSERT INTO core.permission (code, module, description, scope, sensitive)
VALUES ('institution.view', 'institution', 'View institution profile, branding and features', 'TENANT', false),
       ('institution.manage', 'institution', 'Change institution profile, branding and enabled features', 'TENANT', true),
       ('settings.view', 'settings', 'View institution settings', 'TENANT', false),
       ('settings.manage', 'settings', 'Change institution settings', 'TENANT', true),
       ('branch.view', 'branch', 'View branches', 'TENANT', false),
       ('branch.manage', 'branch', 'Create and change branches', 'TENANT', true),
       ('staff.view', 'staff', 'View staff', 'TENANT', false),
       ('staff.create', 'staff', 'Create staff', 'TENANT', true),
       ('staff.edit', 'staff', 'Edit staff profiles', 'TENANT', false),
       ('staff.disable', 'staff', 'Suspend, reactivate or terminate staff', 'TENANT', true),
       ('staff.unlock', 'staff', 'Unlock staff logins and reset credentials', 'TENANT', true),
       ('role.view', 'iam', 'View roles', 'TENANT', false),
       ('role.manage', 'iam', 'Create and change roles', 'TENANT', true),
       ('role.assign', 'iam', 'Assign roles to staff', 'TENANT', true),
       ('permission.view', 'iam', 'View the permission catalog', 'TENANT', false),
       ('audit.view', 'audit', 'View audit logs', 'TENANT', false),
       ('customer.view', 'customer', 'View customers', 'TENANT', false),
       ('customer.create', 'customer', 'Register customers', 'TENANT', false),
       ('customer.edit', 'customer', 'Edit customers', 'TENANT', false),
       ('customer.freeze', 'customer', 'Freeze, restrict and unfreeze customers', 'TENANT', true),
       ('kyc.view', 'kyc', 'View KYC information', 'TENANT', false),
       ('kyc.review', 'kyc', 'Review KYC submissions', 'TENANT', false),
       ('kyc.approve', 'kyc', 'Approve or reject KYC', 'TENANT', true),
       ('product.view', 'product', 'View deposit and loan products', 'TENANT', false),
       ('product.manage', 'product', 'Create and change products', 'TENANT', true),
       ('account.view', 'account', 'View accounts and balances', 'TENANT', false),
       ('account.create', 'account', 'Open accounts', 'TENANT', false),
       ('account.freeze', 'account', 'Freeze and unfreeze accounts, place holds', 'TENANT', true),
       ('account.close', 'account', 'Close accounts', 'TENANT', true),
       ('transaction.view', 'transaction', 'View transactions', 'TENANT', false),
       ('transaction.create', 'transaction', 'Initiate transactions', 'TENANT', false),
       ('transaction.reverse', 'transaction', 'Reverse posted transactions', 'TENANT', true),
       ('ledger.view', 'ledger', 'View the general ledger and journals', 'TENANT', false),
       ('ledger.post', 'ledger', 'Post manual journals', 'TENANT', true),
       ('approval.view', 'approval', 'View approval requests', 'TENANT', false),
       ('approval.act', 'approval', 'Approve or reject approval requests', 'TENANT', true),
       ('teller.operate', 'teller', 'Operate a teller drawer', 'TENANT', false),
       ('teller.supervise', 'teller', 'Supervise tellers and approve balancing differences', 'TENANT', true),
       ('cash.view', 'cash', 'View vault and cash positions', 'TENANT', false),
       ('cash.manage', 'cash', 'Move cash between vaults, drawers and branches', 'TENANT', true),
       ('collection.view', 'collection', 'View field collections', 'TENANT', false),
       ('collection.create', 'collection', 'Record field collections', 'TENANT', false),
       ('loan.view', 'loan', 'View loans and applications', 'TENANT', false),
       ('loan.create', 'loan', 'Create loan applications', 'TENANT', false),
       ('loan.assess', 'loan', 'Perform loan assessments', 'TENANT', false),
       ('loan.recommend', 'loan', 'Recommend loans for approval', 'TENANT', false),
       ('loan.approve', 'loan', 'Approve or reject loans', 'TENANT', true),
       ('loan.disburse', 'loan', 'Disburse approved loans', 'TENANT', true),
       ('loan.writeoff', 'loan', 'Write off loans', 'TENANT', true),
       ('report.view', 'report', 'View reports', 'TENANT', false),
       ('report.export', 'report', 'Export reports', 'TENANT', false),
       ('compliance.view', 'compliance', 'View compliance alerts and cases', 'TENANT', false),
       ('compliance.manage', 'compliance', 'Investigate and decide compliance cases', 'TENANT', true),
       ('fraud.view', 'fraud', 'View fraud alerts', 'TENANT', false),
       ('fraud.manage', 'fraud', 'Investigate and act on fraud alerts', 'TENANT', true),
       ('platform.tenant.view', 'platform', 'View institutions on the platform', 'PLATFORM', false),
       ('platform.tenant.manage', 'platform', 'Onboard, suspend and reactivate institutions', 'PLATFORM', true),
       ('platform.feature.manage', 'platform', 'License features to institutions', 'PLATFORM', true),
       ('platform.audit.view', 'platform', 'View platform audit logs', 'PLATFORM', false),
       ('platform.user.manage', 'platform', 'Manage platform administrators', 'PLATFORM', true);

GRANT SELECT ON core.permission TO ${app_db_role};


-- ---------------------------------------------------------------------------------------------------
-- Roles (tenant-owned collections of permissions)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE core.role
(
    id          uuid         NOT NULL,
    tenant_id   uuid         NOT NULL,
    code        varchar(50)  NOT NULL,
    name        varchar(100) NOT NULL,
    description varchar(255),
    system_role boolean      NOT NULL,
    status      varchar(20)  NOT NULL,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    created_by  uuid,
    updated_by  uuid,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_role PRIMARY KEY (id),
    CONSTRAINT uq_role_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_role_tenant_code UNIQUE (tenant_id, code),
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_role_code CHECK (code ~ '^[A-Z][A-Z0-9_]{1,49}$'),
    CONSTRAINT ck_role_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

SELECT core.apply_tenant_isolation('core.role');
GRANT SELECT, INSERT, UPDATE ON core.role TO ${app_db_role};


CREATE TABLE core.role_permission
(
    role_id         uuid        NOT NULL,
    tenant_id       uuid        NOT NULL,
    permission_code varchar(64) NOT NULL,
    CONSTRAINT pk_role_permission PRIMARY KEY (role_id, permission_code),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (tenant_id, role_id)
        REFERENCES core.role (tenant_id, id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_code) REFERENCES core.permission (code)
);

CREATE INDEX ix_role_permission_tenant_role ON core.role_permission (tenant_id, role_id);

-- Defence in depth: platform-scope permissions can never be granted to a tenant role.
CREATE FUNCTION core.ensure_tenant_scope_permission() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    IF (SELECT scope FROM core.permission WHERE code = NEW.permission_code) IS DISTINCT FROM 'TENANT' THEN
        RAISE EXCEPTION 'Permission % cannot be granted to a tenant role', NEW.permission_code
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_role_permission_tenant_scope
    BEFORE INSERT OR UPDATE
    ON core.role_permission
    FOR EACH ROW
EXECUTE FUNCTION core.ensure_tenant_scope_permission();

SELECT core.apply_tenant_isolation('core.role_permission');
GRANT SELECT, INSERT, DELETE ON core.role_permission TO ${app_db_role};


-- ---------------------------------------------------------------------------------------------------
-- Staff (owned by the staff module; created here because credentials reference it)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE core.staff
(
    id                  uuid         NOT NULL,
    tenant_id           uuid         NOT NULL,
    employee_number     varchar(30)  NOT NULL,
    first_name          varchar(100) NOT NULL,
    last_name           varchar(100) NOT NULL,
    email               varchar(254) NOT NULL,
    phone               varchar(20),
    job_title           varchar(100),
    home_branch_id      uuid         NOT NULL,
    all_branches_access boolean      NOT NULL,
    status              varchar(20)  NOT NULL,
    status_reason       varchar(255),
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    created_by          uuid,
    updated_by          uuid,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_staff PRIMARY KEY (id),
    CONSTRAINT uq_staff_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uq_staff_tenant_employee_number UNIQUE (tenant_id, employee_number),
    CONSTRAINT fk_staff_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT fk_staff_home_branch FOREIGN KEY (tenant_id, home_branch_id) REFERENCES core.branch (tenant_id, id),
    CONSTRAINT ck_staff_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'TERMINATED'))
);

CREATE UNIQUE INDEX uq_staff_tenant_email ON core.staff (tenant_id, lower(email));
CREATE INDEX ix_staff_tenant_home_branch ON core.staff (tenant_id, home_branch_id);

SELECT core.apply_tenant_isolation('core.staff');
GRANT SELECT, INSERT, UPDATE ON core.staff TO ${app_db_role};


CREATE TABLE core.staff_credential
(
    staff_id              uuid         NOT NULL,
    tenant_id             uuid         NOT NULL,
    username              varchar(100) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    password_changed_at   timestamptz  NOT NULL,
    must_change_password  boolean      NOT NULL,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    login_enabled         boolean      NOT NULL,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_staff_credential PRIMARY KEY (staff_id),
    CONSTRAINT uq_staff_credential_tenant_username UNIQUE (tenant_id, username),
    CONSTRAINT fk_staff_credential_staff FOREIGN KEY (tenant_id, staff_id) REFERENCES core.staff (tenant_id, id),
    CONSTRAINT ck_staff_credential_username CHECK (username ~ '^[a-z0-9][a-z0-9._@-]{2,99}$'),
    CONSTRAINT ck_staff_credential_failed_attempts CHECK (failed_login_attempts >= 0)
);

SELECT core.apply_tenant_isolation('core.staff_credential');
GRANT SELECT, INSERT, UPDATE ON core.staff_credential TO ${app_db_role};


CREATE TABLE core.staff_role
(
    staff_id    uuid        NOT NULL,
    role_id     uuid        NOT NULL,
    tenant_id   uuid        NOT NULL,
    assigned_at timestamptz NOT NULL,
    assigned_by uuid,
    CONSTRAINT pk_staff_role PRIMARY KEY (staff_id, role_id),
    CONSTRAINT fk_staff_role_staff FOREIGN KEY (tenant_id, staff_id) REFERENCES core.staff (tenant_id, id),
    CONSTRAINT fk_staff_role_role FOREIGN KEY (tenant_id, role_id) REFERENCES core.role (tenant_id, id)
);

CREATE INDEX ix_staff_role_tenant_role ON core.staff_role (tenant_id, role_id);

SELECT core.apply_tenant_isolation('core.staff_role');
GRANT SELECT, INSERT, DELETE ON core.staff_role TO ${app_db_role};


-- ---------------------------------------------------------------------------------------------------
-- Platform administrators (separate identity store; invisible inside any tenant context)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE core.platform_user
(
    id                    uuid         NOT NULL,
    username              varchar(100) NOT NULL,
    email                 varchar(254) NOT NULL,
    full_name             varchar(200) NOT NULL,
    platform_role         varchar(30)  NOT NULL,
    status                varchar(20)  NOT NULL,
    password_hash         varchar(255) NOT NULL,
    password_changed_at   timestamptz  NOT NULL,
    must_change_password  boolean      NOT NULL,
    failed_login_attempts integer      NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    created_at            timestamptz  NOT NULL,
    updated_at            timestamptz  NOT NULL,
    created_by            uuid,
    updated_by            uuid,
    version               bigint       NOT NULL DEFAULT 0,
    CONSTRAINT pk_platform_user PRIMARY KEY (id),
    CONSTRAINT uq_platform_user_username UNIQUE (username),
    CONSTRAINT ck_platform_user_username CHECK (username ~ '^[a-z0-9][a-z0-9._@-]{2,99}$'),
    CONSTRAINT ck_platform_user_role CHECK (platform_role IN ('PLATFORM_OWNER', 'PLATFORM_ADMIN', 'PLATFORM_SUPPORT')),
    CONSTRAINT ck_platform_user_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_platform_user_failed_attempts CHECK (failed_login_attempts >= 0)
);

CREATE UNIQUE INDEX uq_platform_user_email ON core.platform_user (lower(email));

SELECT core.apply_platform_only_isolation('core.platform_user');
GRANT SELECT, INSERT, UPDATE ON core.platform_user TO ${app_db_role};


-- ---------------------------------------------------------------------------------------------------
-- Sessions and rotating refresh tokens (staff sessions are tenant rows, platform sessions have NULL tenant)
-- ---------------------------------------------------------------------------------------------------
CREATE TABLE core.auth_session
(
    id                uuid        NOT NULL,
    tenant_id         uuid,
    principal_type    varchar(20) NOT NULL,
    principal_id      uuid        NOT NULL,
    status            varchar(20) NOT NULL,
    created_at        timestamptz NOT NULL,
    last_refreshed_at timestamptz NOT NULL,
    expires_at        timestamptz NOT NULL,
    revoked_at        timestamptz,
    revoke_reason     varchar(50),
    ip_address        varchar(45),
    user_agent        varchar(400),
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pk_auth_session PRIMARY KEY (id),
    CONSTRAINT fk_auth_session_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenant (id),
    CONSTRAINT ck_auth_session_principal_type CHECK (principal_type IN ('STAFF', 'PLATFORM')),
    CONSTRAINT ck_auth_session_tenant CHECK ((principal_type = 'PLATFORM') = (tenant_id IS NULL)),
    CONSTRAINT ck_auth_session_status CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT ck_auth_session_revoked CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);

CREATE INDEX ix_auth_session_principal ON core.auth_session (principal_type, principal_id, status);
CREATE INDEX ix_auth_session_tenant_status ON core.auth_session (tenant_id, status);

SELECT core.apply_tenant_or_platform_isolation('core.auth_session');
GRANT SELECT, INSERT, UPDATE, DELETE ON core.auth_session TO ${app_db_role};


CREATE TABLE core.auth_refresh_token
(
    token_hash varchar(64) NOT NULL,
    tenant_id  uuid,
    session_id uuid        NOT NULL,
    issued_at  timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    rotated_at timestamptz,
    CONSTRAINT pk_auth_refresh_token PRIMARY KEY (token_hash),
    CONSTRAINT fk_auth_refresh_token_session FOREIGN KEY (session_id) REFERENCES core.auth_session (id) ON DELETE CASCADE,
    CONSTRAINT ck_auth_refresh_token_hash CHECK (token_hash ~ '^[0-9a-f]{64}$')
);

CREATE INDEX ix_auth_refresh_token_session ON core.auth_refresh_token (session_id);

SELECT core.apply_tenant_or_platform_isolation('core.auth_refresh_token');
GRANT SELECT, INSERT, UPDATE, DELETE ON core.auth_refresh_token TO ${app_db_role};
