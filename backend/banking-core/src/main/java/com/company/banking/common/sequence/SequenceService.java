package com.company.banking.common.sequence;

import com.company.banking.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Types;

/**
 * Gap-free per-tenant counters for human-facing numbers (customer number, account number).
 *
 * <p>The counter row stays locked until the caller's transaction ends, so concurrent allocations serialise and a
 * rolled-back transaction gives its number back. Must therefore be called inside the business transaction that uses
 * the number. For high-volume references (transactions) use database sequences instead; gaps are acceptable there.
 */
@Service
@RequiredArgsConstructor
public class SequenceService {

    private final JdbcClient jdbcClient;

    @Transactional(propagation = Propagation.MANDATORY)
    public long next(String sequenceKey) {
        SqlParameterValue tenantId = new SqlParameterValue(Types.OTHER, TenantContext.requireTenantId());
        jdbcClient.sql("""
                        INSERT INTO core.number_sequence (tenant_id, sequence_key, next_value, updated_at)
                        VALUES (:tenantId, :key, 1, now())
                        ON CONFLICT (tenant_id, sequence_key) DO NOTHING""")
                .param("tenantId", tenantId)
                .param("key", sequenceKey)
                .update();
        return jdbcClient.sql("""
                        UPDATE core.number_sequence
                        SET next_value = next_value + 1, updated_at = now()
                        WHERE tenant_id = :tenantId AND sequence_key = :key
                        RETURNING next_value - 1""")
                .param("tenantId", tenantId)
                .param("key", sequenceKey)
                .query(Long.class)
                .single();
    }
}
