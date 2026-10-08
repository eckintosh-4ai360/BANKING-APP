package com.company.banking.ledger.repository;

import static com.company.banking.ledger.repository.SqlParams.date;
import static com.company.banking.ledger.repository.SqlParams.uuid;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AccountingPeriodRepository {

    public record PeriodRow(LocalDate periodStart, LocalDate periodEnd, String status, Instant closedAt,
                            UUID closedBy) {
    }

    private static final String COLUMNS = "period_start, period_end, status, closed_at, closed_by";

    private static final RowMapper<PeriodRow> MAPPER = (rs, rowNum) -> new PeriodRow(
            rs.getObject("period_start", LocalDate.class),
            rs.getObject("period_end", LocalDate.class),
            rs.getString("status"),
            rs.getTimestamp("closed_at") == null ? null : rs.getTimestamp("closed_at").toInstant(),
            rs.getObject("closed_by", UUID.class));

    private final JdbcClient jdbc;

    public Optional<PeriodRow> findCovering(UUID tenantId, LocalDate day) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.accounting_period"
                        + " WHERE tenant_id = :tenantId AND :day BETWEEN period_start AND period_end")
                .param("tenantId", uuid(tenantId))
                .param("day", date(day))
                .query(MAPPER)
                .optional();
    }

    public Optional<PeriodRow> lockForUpdate(UUID tenantId, LocalDate periodStart) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.accounting_period"
                        + " WHERE tenant_id = :tenantId AND period_start = :start FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .param("start", date(periodStart))
                .query(MAPPER)
                .optional();
    }

    /**
     * End of the latest closed period: nothing may be opened or posted on or before it.
     */
    public Optional<LocalDate> latestClosedEnd(UUID tenantId) {
        return jdbc.sql("SELECT max(period_end) FROM core.accounting_period"
                        + " WHERE tenant_id = :tenantId AND status = 'CLOSED'")
                .param("tenantId", uuid(tenantId))
                .query(LocalDate.class)
                .optional();
    }

    public boolean hasUnclosedBefore(UUID tenantId, LocalDate periodStart) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.accounting_period"
                        + " WHERE tenant_id = :tenantId AND period_start < :start AND status <> 'CLOSED')")
                .param("tenantId", uuid(tenantId))
                .param("start", date(periodStart))
                .query(Boolean.class)
                .single();
    }

    /**
     * Opens a calendar-month period; concurrent callers converge on the same row.
     */
    public void insertOpenIfAbsent(UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        jdbc.sql("INSERT INTO core.accounting_period (tenant_id, period_start, period_end, status)"
                        + " VALUES (:tenantId, :start, :end, 'OPEN')"
                        + " ON CONFLICT (tenant_id, period_start) DO NOTHING")
                .param("tenantId", uuid(tenantId))
                .param("start", date(periodStart))
                .param("end", date(periodEnd))
                .update();
    }

    public void close(UUID tenantId, LocalDate periodStart, Instant closedAt, UUID closedBy) {
        jdbc.sql("UPDATE core.accounting_period SET status = 'CLOSED', closed_at = :closedAt, closed_by = :closedBy"
                        + " WHERE tenant_id = :tenantId AND period_start = :start AND status = 'OPEN'")
                .param("tenantId", uuid(tenantId))
                .param("start", date(periodStart))
                .param("closedAt", Timestamp.from(closedAt))
                .param("closedBy", uuid(closedBy))
                .update();
    }

    public List<PeriodRow> list(UUID tenantId, int limit, long offset) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM core.accounting_period WHERE tenant_id = :tenantId"
                        + " ORDER BY period_start DESC LIMIT :limit OFFSET :offset")
                .param("tenantId", uuid(tenantId))
                .param("limit", limit)
                .param("offset", offset)
                .query(MAPPER)
                .list();
    }

    public long count(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM core.accounting_period WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .query(Long.class)
                .single();
    }
}
