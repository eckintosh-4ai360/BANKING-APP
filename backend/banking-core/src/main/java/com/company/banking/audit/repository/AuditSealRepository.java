package com.company.banking.audit.repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Audit seals (insert-only). Runs inside the caller's transaction, so row-level security limits every query to the
 * caller's trail.
 */
@Repository
@RequiredArgsConstructor
public class AuditSealRepository {

    /** First key of the seal lock; the database's audit insert trigger uses the same pair. */
    private static final int SEAL_LOCK = 1096107091;

    private static final String COLUMNS = "id, tenant_id, sequence_no, range_start, range_end, row_count, merkle_root,"
            + " previous_hash, seal_hash, key_version, signature, created_at";

    private static final RowMapper<AuditSealRow> ROW_MAPPER = (rs, rowNum) -> new AuditSealRow(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getLong("sequence_no"),
            rs.getTimestamp("range_start").toInstant(),
            rs.getTimestamp("range_end").toInstant(),
            rs.getInt("row_count"),
            rs.getString("merkle_root"),
            rs.getString("previous_hash"),
            rs.getString("seal_hash"),
            rs.getInt("key_version"),
            rs.getString("signature"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbcClient;

    /**
     * Takes the trail's seal lock until the transaction ends. Every audit insert holds the same lock shared until it
     * commits, so once this returns no audit row of the trail is still in flight. Gives up after {@code timeoutMs}.
     */
    public void lockTrail(UUID tenantId, int timeoutMs) {
        jdbcClient.sql("SELECT set_config('lock_timeout', :timeout, true)")
                .param("timeout", timeoutMs + "ms")
                .query(String.class)
                .single();
        jdbcClient.sql("SELECT 1 FROM pg_advisory_xact_lock(:lock, hashtext(:scope))")
                .param("lock", SEAL_LOCK)
                .param("scope", tenantId == null ? "platform" : tenantId.toString())
                .query(Integer.class)
                .single();
    }

    public Optional<AuditSealRow> last(UUID tenantId) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_seal WHERE " + scope(tenantId)
                        + " ORDER BY sequence_no DESC LIMIT 1")
                .param("tenantId", uuid(tenantId))
                .query(ROW_MAPPER)
                .optional();
    }

    public void insert(AuditSealRow seal) {
        jdbcClient.sql("""
                        INSERT INTO core.audit_seal (id, tenant_id, sequence_no, range_start, range_end, row_count,
                            merkle_root, previous_hash, seal_hash, key_version, signature, created_at)
                        VALUES (:id, :tenantId, :sequenceNo, :rangeStart, :rangeEnd, :rowCount,
                            :merkleRoot, :previousHash, :sealHash, :keyVersion, :signature, :createdAt)""")
                .param("id", uuid(seal.id()))
                .param("tenantId", uuid(seal.tenantId()))
                .param("sequenceNo", seal.sequenceNo())
                .param("rangeStart", Timestamp.from(seal.rangeStart()))
                .param("rangeEnd", Timestamp.from(seal.rangeEnd()))
                .param("rowCount", seal.rowCount())
                .param("merkleRoot", seal.merkleRoot())
                .param("previousHash", seal.previousHash())
                .param("sealHash", seal.sealHash())
                .param("keyVersion", seal.keyVersion())
                .param("signature", seal.signature())
                .param("createdAt", Timestamp.from(seal.createdAt()))
                .update();
    }

    /**
     * Seals overlapping {@code [from, to)}, oldest first.
     */
    public List<AuditSealRow> overlapping(UUID tenantId, Instant from, Instant to) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_seal WHERE " + scope(tenantId)
                        + " AND range_end > :from AND range_start < :to ORDER BY sequence_no")
                .param("tenantId", uuid(tenantId))
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query(ROW_MAPPER)
                .list();
    }

    public Optional<AuditSealRow> bySequence(UUID tenantId, long sequenceNo) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_seal WHERE " + scope(tenantId)
                        + " AND sequence_no = :sequenceNo")
                .param("tenantId", uuid(tenantId))
                .param("sequenceNo", sequenceNo)
                .query(ROW_MAPPER)
                .optional();
    }

    /** Newest first. */
    public List<AuditSealRow> page(UUID tenantId, int offset, int limit) {
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_seal WHERE " + scope(tenantId)
                        + " ORDER BY sequence_no DESC LIMIT :limit OFFSET :offset")
                .param("tenantId", uuid(tenantId))
                .param("limit", limit)
                .param("offset", offset)
                .query(ROW_MAPPER)
                .list();
    }

    public long count(UUID tenantId) {
        return jdbcClient.sql("SELECT count(*) FROM core.audit_seal WHERE " + scope(tenantId))
                .param("tenantId", uuid(tenantId))
                .query(Long.class)
                .single();
    }

    private static String scope(UUID tenantId) {
        return tenantId == null ? "tenant_id IS NULL" : "tenant_id = :tenantId";
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }
}
