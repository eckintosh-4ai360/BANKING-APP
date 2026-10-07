-- =====================================================================================================
-- V1 Baseline: schema-level helpers shared by every module.
--
-- Runs as the schema owner (banking_migrator). The runtime role ${app_db_role} is NOT the owner, is not a
-- superuser and has no BYPASSRLS, so row-level security applies to it. It receives explicit, minimal
-- grants per table in each migration.
-- =====================================================================================================

GRANT USAGE ON SCHEMA core TO ${app_db_role};

-- Tenant of the current database transaction, set by the application at transaction start via
--     SELECT set_config('app.tenant_id', '<uuid>', true)
-- Returns NULL when unset, which makes every tenant policy evaluate to false (fail closed).
CREATE FUNCTION core.current_tenant_id() RETURNS uuid
    LANGUAGE sql
    STABLE
    PARALLEL SAFE
AS
$$
SELECT NULLIF(current_setting('app.tenant_id', true), '')::uuid
$$;

-- Tenant-owned table: rows are visible and writable only inside the owning tenant's context.
CREATE FUNCTION core.apply_tenant_isolation(target regclass) RETURNS void
    LANGUAGE plpgsql
AS
$$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
    EXECUTE format('CREATE POLICY tenant_isolation ON %s '
                       || 'USING (tenant_id = core.current_tenant_id()) '
                       || 'WITH CHECK (tenant_id = core.current_tenant_id())', target);
END;
$$;

-- Table holding both tenant rows and platform rows (tenant_id IS NULL). Platform rows are visible only when
-- no tenant is set; tenant rows only inside their tenant.
CREATE FUNCTION core.apply_tenant_or_platform_isolation(target regclass) RETURNS void
    LANGUAGE plpgsql
AS
$$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
    EXECUTE format('CREATE POLICY tenant_or_platform_isolation ON %s '
                       || 'USING (tenant_id IS NOT DISTINCT FROM core.current_tenant_id()) '
                       || 'WITH CHECK (tenant_id IS NOT DISTINCT FROM core.current_tenant_id())', target);
END;
$$;

-- Platform-only table: invisible whenever a tenant context is active.
CREATE FUNCTION core.apply_platform_only_isolation(target regclass) RETURNS void
    LANGUAGE plpgsql
AS
$$
BEGIN
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', target);
    EXECUTE format('CREATE POLICY platform_only ON %s '
                       || 'USING (core.current_tenant_id() IS NULL) '
                       || 'WITH CHECK (core.current_tenant_id() IS NULL)', target);
END;
$$;

REVOKE ALL ON FUNCTION core.apply_tenant_isolation(regclass) FROM PUBLIC;
REVOKE ALL ON FUNCTION core.apply_tenant_or_platform_isolation(regclass) FROM PUBLIC;
REVOKE ALL ON FUNCTION core.apply_platform_only_isolation(regclass) FROM PUBLIC;

-- Trigger function for append-only tables (audit log, ledger). Fires even for the table owner.
CREATE FUNCTION core.reject_mutation() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
BEGIN
    RAISE EXCEPTION 'Table %.% is append-only: % is not permitted', TG_TABLE_SCHEMA, TG_TABLE_NAME, TG_OP
        USING ERRCODE = 'insufficient_privilege';
END;
$$;
