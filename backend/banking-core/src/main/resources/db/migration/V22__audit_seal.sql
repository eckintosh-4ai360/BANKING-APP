-- =====================================================================================================
-- V22 Audit seals: a signed hash chain over the audit trail, one seal per period (an hour by default).
--
-- Each seal covers one scope (an institution, or the platform's own events) for [range_start, range_end): the
-- number of rows, the Merkle root of their hashes, the previous seal's hash and an HMAC signature. Changing,
-- removing or adding an audit row afterwards (even as a superuser, with triggers disabled) breaks the seal.
-- =====================================================================================================

CREATE TABLE core.audit_seal
(
    id            uuid        NOT NULL,
    tenant_id     uuid,
    sequence_no   bigint      NOT NULL,
    range_start   timestamptz NOT NULL,
    range_end     timestamptz NOT NULL,
    row_count     integer     NOT NULL,
    merkle_root   varchar(64) NOT NULL,
    previous_hash varchar(64) NOT NULL,
    seal_hash     varchar(64) NOT NULL,
    key_version   integer     NOT NULL,
    signature     varchar(64) NOT NULL,
    created_at    timestamptz NOT NULL,
    CONSTRAINT pk_audit_seal PRIMARY KEY (id),
    CONSTRAINT uq_audit_seal_sequence UNIQUE NULLS NOT DISTINCT (tenant_id, sequence_no),
    CONSTRAINT uq_audit_seal_start UNIQUE NULLS NOT DISTINCT (tenant_id, range_start),
    CONSTRAINT ck_audit_seal_range CHECK (range_end > range_start),
    CONSTRAINT ck_audit_seal_sequence CHECK (sequence_no > 0),
    CONSTRAINT ck_audit_seal_count CHECK (row_count >= 0),
    CONSTRAINT ck_audit_seal_hashes CHECK (merkle_root ~ '^[0-9a-f]{64}$' AND previous_hash ~ '^[0-9a-f]{64}$'
        AND seal_hash ~ '^[0-9a-f]{64}$' AND signature ~ '^[0-9a-f]{64}$')
);

COMMENT ON TABLE core.audit_seal IS
    'Hash chain over core.audit_log; tenant_id NULL is the platform''s own trail';

CREATE INDEX ix_audit_seal_scope_end ON core.audit_seal (tenant_id, range_end DESC);

-- A seal continues its scope's chain: next sequence number, starting where the last seal ended, linked to its hash.
CREATE FUNCTION core.audit_seal_chain_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    last core.audit_seal%ROWTYPE;
BEGIN
    SELECT * INTO last FROM core.audit_seal
    WHERE tenant_id IS NOT DISTINCT FROM NEW.tenant_id
    ORDER BY sequence_no DESC
    LIMIT 1;
    IF last.id IS NULL THEN
        IF NEW.sequence_no <> 1 OR NEW.previous_hash <> repeat('0', 64) THEN
            RAISE EXCEPTION 'The first audit seal must have sequence 1 and no previous hash'
                USING ERRCODE = 'check_violation';
        END IF;
    ELSIF NEW.sequence_no <> last.sequence_no + 1
        OR NEW.range_start <> last.range_end
        OR NEW.previous_hash <> last.seal_hash THEN
        RAISE EXCEPTION 'An audit seal must continue the chain after seal %', last.sequence_no
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_seal_chain
    BEFORE INSERT ON core.audit_seal
    FOR EACH ROW EXECUTE FUNCTION core.audit_seal_chain_guard();
CREATE TRIGGER trg_audit_seal_immutable
    BEFORE UPDATE OR DELETE ON core.audit_seal
    FOR EACH ROW EXECUTE FUNCTION core.reject_mutation();
CREATE TRIGGER trg_audit_seal_no_truncate
    BEFORE TRUNCATE ON core.audit_seal
    FOR EACH STATEMENT EXECUTE FUNCTION core.reject_mutation();

SELECT core.apply_tenant_or_platform_isolation('core.audit_seal');
GRANT SELECT, INSERT ON core.audit_seal TO ${app_db_role};

-- Every audit insert holds its scope's seal lock (shared) until it commits. The sealer takes the lock exclusively,
-- so it waits for audit rows still in flight and never seals a period that a running transaction is writing to.
-- Rows dated inside a sealed period are refused.
CREATE FUNCTION core.audit_log_seal_guard() RETURNS trigger
    LANGUAGE plpgsql
AS
$$
DECLARE
    sealed_to timestamptz;
BEGIN
    PERFORM pg_advisory_xact_lock_shared(1096107091, hashtext(coalesce(NEW.tenant_id::text, 'platform')));
    IF NEW.tenant_id IS NULL THEN
        SELECT max(range_end) INTO sealed_to FROM core.audit_seal WHERE tenant_id IS NULL;
    ELSE
        SELECT max(range_end) INTO sealed_to FROM core.audit_seal WHERE tenant_id = NEW.tenant_id;
    END IF;
    IF sealed_to IS NOT NULL AND NEW.occurred_at < sealed_to THEN
        RAISE EXCEPTION 'The audit trail is sealed up to %', sealed_to USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_audit_log_seal_guard
    BEFORE INSERT ON core.audit_log
    FOR EACH ROW EXECUTE FUNCTION core.audit_log_seal_guard();
