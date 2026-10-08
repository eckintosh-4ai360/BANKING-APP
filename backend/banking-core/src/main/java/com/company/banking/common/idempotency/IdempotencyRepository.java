package com.company.banking.common.idempotency;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class IdempotencyRepository {

    public record StoredRecord(UUID id, String requestHash, String status, String responseBody) {
    }

    private final JdbcClient jdbc;

    /**
     * Claims the key. Returns false when the key exists; if another transaction is inserting the same key right now,
     * this waits until it commits or rolls back.
     */
    public boolean tryInsert(UUID id, UUID tenantId, String scope, String key, String requestHash,
                             String resourceType, Instant now, Instant expiresAt) {
        return jdbc.sql("INSERT INTO core.idempotency_record (id, tenant_id, scope, idempotency_key, request_hash,"
                        + " status, resource_type, created_at, expires_at)"
                        + " VALUES (:id, :tenantId, :scope, :key, :hash, 'IN_PROGRESS', :resourceType, :now, :expiresAt)"
                        + " ON CONFLICT (tenant_id, scope, idempotency_key) DO NOTHING")
                .param("id", uuid(id))
                .param("tenantId", uuid(tenantId))
                .param("scope", scope)
                .param("key", key)
                .param("hash", requestHash)
                .param("resourceType", new SqlParameterValue(Types.VARCHAR, resourceType))
                .param("now", Timestamp.from(now))
                .param("expiresAt", Timestamp.from(expiresAt))
                .update() == 1;
    }

    public Optional<StoredRecord> lock(UUID tenantId, String scope, String key) {
        return jdbc.sql("SELECT id, request_hash, status, response_body::text AS body FROM core.idempotency_record"
                        + " WHERE tenant_id = :tenantId AND scope = :scope AND idempotency_key = :key FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .param("scope", scope)
                .param("key", key)
                .query((rs, rowNum) -> new StoredRecord(rs.getObject("id", UUID.class), rs.getString("request_hash"),
                        rs.getString("status"), rs.getString("body")))
                .optional();
    }

    public void complete(UUID id, String responseJson, UUID resourceId, Instant now) {
        jdbc.sql("UPDATE core.idempotency_record SET status = 'COMPLETED', response_body = CAST(:body AS jsonb),"
                        + " resource_id = :resourceId, completed_at = :now WHERE id = :id")
                .param("id", uuid(id))
                .param("body", responseJson)
                .param("resourceId", uuid(resourceId))
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * An expired key is free again.
     */
    public void deleteExpired(UUID tenantId, String scope, String key, Instant now) {
        jdbc.sql("DELETE FROM core.idempotency_record WHERE tenant_id = :tenantId AND scope = :scope"
                        + " AND idempotency_key = :key AND expires_at < :now")
                .param("tenantId", uuid(tenantId))
                .param("scope", scope)
                .param("key", key)
                .param("now", Timestamp.from(now))
                .update();
    }

    public int purgeExpired(UUID tenantId, Instant now, int limit) {
        return jdbc.sql("DELETE FROM core.idempotency_record WHERE id IN (SELECT id FROM core.idempotency_record"
                        + " WHERE tenant_id = :tenantId AND expires_at < :now LIMIT :limit)")
                .param("tenantId", uuid(tenantId))
                .param("now", Timestamp.from(now))
                .param("limit", limit)
                .update();
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }
}
