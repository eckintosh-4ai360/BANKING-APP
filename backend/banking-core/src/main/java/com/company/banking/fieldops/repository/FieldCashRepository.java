package com.company.banking.fieldops.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * What an officer's collections and remittances add up to, to set against the officer's ledger balance.
 */
@Repository
@RequiredArgsConstructor
public class FieldCashRepository {

    private final JdbcClient jdbc;

    /**
     * Posted collections whose transaction was not reversed, on {@code businessDate} or ever (when null).
     */
    public BigDecimal collected(UUID tenantId, UUID officerId, LocalDate businessDate) {
        return jdbc.sql("""
                        SELECT coalesce(sum(c.amount), 0) FROM core.collection c
                        JOIN core.financial_transaction t ON t.tenant_id = c.tenant_id
                         AND t.id = c.financial_transaction_id
                        WHERE c.tenant_id = :tenantId AND c.officer_id = :officerId AND c.status = 'POSTED'
                          AND t.status = 'POSTED' AND (CAST(:date AS date) IS NULL OR c.business_date = :date)""")
                .param("tenantId", uuid(tenantId))
                .param("officerId", uuid(officerId))
                .param("date", date(businessDate))
                .query(BigDecimal.class)
                .single();
    }

    /**
     * Cash the officer handed to tellers, on {@code businessDate} or ever (when null).
     */
    public BigDecimal remitted(UUID tenantId, UUID officerId, LocalDate businessDate) {
        return jdbc.sql("""
                        SELECT coalesce(sum(amount), 0) FROM core.collector_remittance
                        WHERE tenant_id = :tenantId AND officer_id = :officerId
                          AND (CAST(:date AS date) IS NULL OR business_date = :date)""")
                .param("tenantId", uuid(tenantId))
                .param("officerId", uuid(officerId))
                .param("date", date(businessDate))
                .query(BigDecimal.class)
                .single();
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }

    private static SqlParameterValue date(LocalDate value) {
        return new SqlParameterValue(Types.DATE, value == null ? null : Date.valueOf(value));
    }
}
