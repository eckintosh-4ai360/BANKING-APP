package com.company.banking.susu.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Commission charged at the end of each susu cycle (insert-only).
 */
@Repository
@RequiredArgsConstructor
public class SusuCommissionRepository {

    public record Commission(int cycleNo, BigDecimal amountDue, BigDecimal amountCharged, LocalDate businessDate) {
    }

    private final JdbcClient jdbc;

    public boolean exists(UUID tenantId, UUID planId, int cycleNo) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.susu_cycle_commission"
                        + " WHERE tenant_id = :tenantId AND plan_id = :planId AND cycle_no = :cycle)")
                .param("tenantId", uuid(tenantId))
                .param("planId", uuid(planId))
                .param("cycle", cycleNo)
                .query(Boolean.class)
                .single();
    }

    @SuppressWarnings("java:S107")
    public void insert(UUID tenantId, UUID planId, int cycleNo, BigDecimal amountDue, BigDecimal amountCharged,
                       UUID journalId, LocalDate businessDate, Instant now) {
        jdbc.sql("""
                        INSERT INTO core.susu_cycle_commission (plan_id, cycle_no, tenant_id, amount_due,
                            amount_charged, journal_entry_id, business_date, created_at)
                        VALUES (:planId, :cycle, :tenantId, :due, :charged, :journalId, :date, :now)""")
                .param("planId", uuid(planId))
                .param("cycle", cycleNo)
                .param("tenantId", uuid(tenantId))
                .param("due", amountDue)
                .param("charged", amountCharged)
                .param("journalId", uuid(journalId))
                .param("date", new SqlParameterValue(Types.DATE, Date.valueOf(businessDate)))
                .param("now", Timestamp.from(now))
                .update();
    }

    public List<Commission> ofPlan(UUID tenantId, UUID planId) {
        return jdbc.sql("SELECT cycle_no, amount_due, amount_charged, business_date FROM core.susu_cycle_commission"
                        + " WHERE tenant_id = :tenantId AND plan_id = :planId ORDER BY cycle_no")
                .param("tenantId", uuid(tenantId))
                .param("planId", uuid(planId))
                .query((rs, rowNum) -> new Commission(rs.getInt("cycle_no"), rs.getBigDecimal("amount_due"),
                        rs.getBigDecimal("amount_charged"), rs.getObject("business_date", LocalDate.class)))
                .list();
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }
}
