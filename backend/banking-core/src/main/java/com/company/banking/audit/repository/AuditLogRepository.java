package com.company.banking.audit.repository;

import com.company.banking.audit.dto.AuditLogSearchCriteria;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Append-only access to {@code core.audit_log} via JDBC (the table is insert-only, so no ORM state is needed).
 * Always runs inside the caller's transaction, so the tenant setting for row-level security is present.
 */
@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private static final String COLUMNS = """
            id, tenant_id, occurred_at, actor_type, actor_id, actor_name, action, outcome, resource_type,
            resource_id, resource_reference, branch_id, before_state::text AS before_state,
            after_state::text AS after_state, metadata::text AS metadata, ip_address, user_agent, device_id,
            correlation_id""";

    private static final RowMapper<AuditLogRow> ROW_MAPPER = (rs, rowNum) -> new AuditLogRow(
            rs.getObject("id", UUID.class),
            rs.getObject("tenant_id", UUID.class),
            rs.getTimestamp("occurred_at").toInstant(),
            rs.getString("actor_type"),
            rs.getObject("actor_id", UUID.class),
            rs.getString("actor_name"),
            rs.getString("action"),
            rs.getString("outcome"),
            rs.getString("resource_type"),
            rs.getString("resource_id"),
            rs.getString("resource_reference"),
            rs.getObject("branch_id", UUID.class),
            rs.getString("before_state"),
            rs.getString("after_state"),
            rs.getString("metadata"),
            rs.getString("ip_address"),
            rs.getString("user_agent"),
            rs.getString("device_id"),
            rs.getString("correlation_id"));

    private final JdbcClient jdbcClient;

    public void insert(AuditLogRow row) {
        jdbcClient.sql("""
                        INSERT INTO core.audit_log (id, tenant_id, occurred_at, actor_type, actor_id, actor_name,
                            action, outcome, resource_type, resource_id, resource_reference, branch_id,
                            before_state, after_state, metadata, ip_address, user_agent, device_id, correlation_id)
                        VALUES (:id, :tenantId, :occurredAt, :actorType, :actorId, :actorName,
                            :action, :outcome, :resourceType, :resourceId, :resourceReference, :branchId,
                            CAST(:beforeState AS jsonb), CAST(:afterState AS jsonb), CAST(:metadata AS jsonb),
                            :ipAddress, :userAgent, :deviceId, :correlationId)
                        """)
                .param("id", uuid(row.id()))
                .param("tenantId", uuid(row.tenantId()))
                .param("occurredAt", Timestamp.from(row.occurredAt()))
                .param("actorType", row.actorType())
                .param("actorId", uuid(row.actorId()))
                .param("actorName", text(row.actorName()))
                .param("action", row.action())
                .param("outcome", row.outcome())
                .param("resourceType", row.resourceType())
                .param("resourceId", text(row.resourceId()))
                .param("resourceReference", text(row.resourceReference()))
                .param("branchId", uuid(row.branchId()))
                .param("beforeState", text(row.beforeState()))
                .param("afterState", text(row.afterState()))
                .param("metadata", text(row.metadata()))
                .param("ipAddress", text(row.ipAddress()))
                .param("userAgent", text(row.userAgent()))
                .param("deviceId", text(row.deviceId()))
                .param("correlationId", text(row.correlationId()))
                .update();
    }

    /**
     * @param tenantId tenant to read, or {@code null} for platform-level events. Row-level security enforces the
     *                 same restriction independently.
     */
    public List<AuditLogRow> search(UUID tenantId, AuditLogSearchCriteria criteria, int offset, int limit) {
        Query query = where(tenantId, criteria);
        query.params.put("limit", limit);
        query.params.put("offset", offset);
        return jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_log WHERE " + query.where
                        + " ORDER BY occurred_at DESC, id DESC LIMIT :limit OFFSET :offset")
                .params(query.params)
                .query(ROW_MAPPER)
                .list();
    }

    public long count(UUID tenantId, AuditLogSearchCriteria criteria) {
        Query query = where(tenantId, criteria);
        return jdbcClient.sql("SELECT count(*) FROM core.audit_log WHERE " + query.where)
                .params(query.params)
                .query(Long.class)
                .single();
    }

    /**
     * The rows of one trail in {@code [from, to)}, in {@code (occurred_at, id)} order, one at a time (for sealing).
     *
     * @param tenantId the institution, or {@code null} for platform-level events
     */
    public void forEachInRange(UUID tenantId, Instant from, Instant to, Consumer<AuditLogRow> action) {
        jdbcClient.sql("SELECT " + COLUMNS + " FROM core.audit_log WHERE " + scope(tenantId)
                        + " AND occurred_at >= :from AND occurred_at < :to ORDER BY occurred_at, id")
                .param("tenantId", uuid(tenantId))
                .param("from", Timestamp.from(from))
                .param("to", Timestamp.from(to))
                .query((RowCallbackHandler) rs -> action.accept(ROW_MAPPER.mapRow(rs, rs.getRow())));
    }

    /**
     * When the trail's first row was written, if it has any.
     */
    public Optional<Instant> earliest(UUID tenantId) {
        return jdbcClient.sql("SELECT min(occurred_at) FROM core.audit_log WHERE " + scope(tenantId))
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> Optional.ofNullable(rs.getTimestamp(1)).map(Timestamp::toInstant))
                .single();
    }

    /** Matches one trail with a predicate the {@code (tenant_id, occurred_at)} index can serve. */
    private static String scope(UUID tenantId) {
        return tenantId == null ? "tenant_id IS NULL" : "tenant_id = :tenantId";
    }

    private static Query where(UUID tenantId, AuditLogSearchCriteria criteria) {
        List<String> conditions = new ArrayList<>();
        Map<String, Object> params = new LinkedHashMap<>();
        conditions.add("tenant_id IS NOT DISTINCT FROM :tenantId");
        params.put("tenantId", uuid(tenantId));
        if (criteria.from() != null) {
            conditions.add("occurred_at >= :from");
            params.put("from", Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            conditions.add("occurred_at < :to");
            params.put("to", Timestamp.from(criteria.to()));
        }
        if (criteria.actorId() != null) {
            conditions.add("actor_id = :actorId");
            params.put("actorId", uuid(criteria.actorId()));
        }
        if (criteria.action() != null) {
            conditions.add("action = :action");
            params.put("action", criteria.action());
        }
        if (criteria.resourceType() != null) {
            conditions.add("resource_type = :resourceType");
            params.put("resourceType", criteria.resourceType());
        }
        if (criteria.resourceId() != null) {
            conditions.add("resource_id = :resourceId");
            params.put("resourceId", criteria.resourceId());
        }
        if (criteria.outcome() != null) {
            conditions.add("outcome = :outcome");
            params.put("outcome", criteria.outcome());
        }
        return new Query(String.join(" AND ", conditions), params);
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }

    private static SqlParameterValue text(String value) {
        return new SqlParameterValue(Types.VARCHAR, value);
    }

    private record Query(String where, Map<String, Object> params) {
    }
}
