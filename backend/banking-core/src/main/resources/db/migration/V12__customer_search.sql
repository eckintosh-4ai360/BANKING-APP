-- =====================================================================================================
-- V12 Index-assisted customer search under row-level security.
--
-- PostgreSQL never uses non-LEAKPROOF operators (LIKE, lower(), trigram) as index conditions on a table with
-- row-level security, because the index would evaluate them on other tenants' rows before the policy filters
-- those out. Partial-name search therefore fell back to scanning every customer of every institution.
--
-- This function is the controlled exception: it runs as the table owner (so the trigram indexes are usable) and
-- enforces the tenant itself from the same session setting the RLS policies use. It returns only ids; callers load
-- the rows through the normal, RLS-protected path. Without a tenant set it returns nothing (fail closed).
-- =====================================================================================================

CREATE FUNCTION core.search_customers(
    p_name_pattern  text,
    p_exact         text,
    p_phone_pattern text,
    p_email         text,
    p_branch_ids    uuid[],
    p_branch_id     uuid,
    p_status        text,
    p_kyc_status    text,
    p_customer_type text,
    p_limit         integer,
    p_offset        integer)
    RETURNS TABLE
            (
                customer_id uuid,
                total_count bigint
            )
    LANGUAGE plpgsql
    STABLE
    SECURITY DEFINER
    SET search_path = core, pg_temp
AS
$$
DECLARE
    v_tenant uuid := core.current_tenant_id();
    v_sql    text;
BEGIN
    IF v_tenant IS NULL THEN
        RETURN;
    END IF;
    v_sql := 'SELECT c.id, count(*) OVER () FROM core.customer c WHERE c.tenant_id = $1';
    IF p_branch_ids IS NOT NULL THEN
        v_sql := v_sql || ' AND c.home_branch_id = ANY ($6)';
    END IF;
    IF p_branch_id IS NOT NULL THEN
        v_sql := v_sql || ' AND c.home_branch_id = $7';
    END IF;
    IF p_status IS NOT NULL THEN
        v_sql := v_sql || ' AND c.status = $8';
    END IF;
    IF p_kyc_status IS NOT NULL THEN
        v_sql := v_sql || ' AND c.kyc_status = $9';
    END IF;
    IF p_customer_type IS NOT NULL THEN
        v_sql := v_sql || ' AND c.customer_type = $10';
    END IF;
    IF p_name_pattern IS NOT NULL THEN
        v_sql := v_sql || ' AND (lower(c.display_name) LIKE $2 OR c.customer_number = $3'
                     || CASE WHEN p_phone_pattern IS NOT NULL THEN ' OR c.primary_phone LIKE $4' ELSE '' END
                     || CASE WHEN p_email IS NOT NULL THEN ' OR lower(c.email) = $5' ELSE '' END
            || ')';
    END IF;
    v_sql := v_sql || ' ORDER BY c.display_name, c.id LIMIT $11 OFFSET $12';
    -- EXECUTE plans with the actual parameter values, so the trigram indexes are chosen for selective patterns.
    RETURN QUERY EXECUTE v_sql
        USING v_tenant, p_name_pattern, p_exact, p_phone_pattern, p_email, p_branch_ids, p_branch_id, p_status,
            p_kyc_status, p_customer_type, p_limit, p_offset;
END;
$$;

REVOKE ALL ON FUNCTION core.search_customers(text, text, text, text, uuid[], uuid, text, text, text, integer, integer)
    FROM PUBLIC;
GRANT EXECUTE ON FUNCTION core.search_customers(text, text, text, text, uuid[], uuid, text, text, text, integer,
    integer) TO ${app_db_role};
