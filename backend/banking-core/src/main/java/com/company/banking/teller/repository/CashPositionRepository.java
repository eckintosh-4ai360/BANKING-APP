package com.company.banking.teller.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Daily cash positions (insert-only, written by end-of-day).
 */
@Repository
@RequiredArgsConstructor
public class CashPositionRepository {

    public record Position(UUID cashPointId, String cashPointType, UUID branchId, String currency,
                           BigDecimal ledgerBalance, BigDecimal countedBalance, UUID tellerSessionId,
                           Instant countedAt, BigDecimal difference, String status) {
    }

    /** The closing count of a drawer's last closed session. */
    public record LastCount(UUID sessionId, BigDecimal counted, Instant closedAt) {
    }

    private static final RowMapper<Position> ROW_MAPPER = (rs, rowNum) -> new Position(
            rs.getObject("cash_point_id", UUID.class),
            rs.getString("cash_point_type"),
            rs.getObject("branch_id", UUID.class),
            rs.getString("currency"),
            rs.getBigDecimal("ledger_balance"),
            rs.getBigDecimal("counted_balance"),
            rs.getObject("teller_session_id", UUID.class),
            Optional.ofNullable(rs.getTimestamp("counted_at")).map(Timestamp::toInstant).orElse(null),
            rs.getBigDecimal("difference"),
            rs.getString("status"));

    private final JdbcClient jdbc;

    /**
     * Per drawer, the closing count of its last session closed for a business date up to {@code date}.
     */
    public Map<UUID, LastCount> lastCounts(UUID tenantId, LocalDate date) {
        Map<UUID, LastCount> counts = new HashMap<>();
        jdbc.sql("""
                        SELECT DISTINCT ON (cash_drawer_id) cash_drawer_id, id, counted_balance, closed_at
                        FROM core.teller_session
                        WHERE tenant_id = :tenantId AND business_date <= :date
                          AND status IN ('CLOSED', 'CLOSED_WITH_DIFFERENCE')
                        ORDER BY cash_drawer_id, closed_at DESC, id DESC""")
                .param("tenantId", uuid(tenantId))
                .param("date", date(date))
                .query((rs, rowNum) -> counts.put(rs.getObject("cash_drawer_id", UUID.class), new LastCount(
                        rs.getObject("id", UUID.class), rs.getBigDecimal("counted_balance"),
                        rs.getTimestamp("closed_at").toInstant())))
                .list();
        return counts;
    }

    /**
     * @return whether the position was written (false when this date's position of the cash point already exists)
     */
    public boolean insert(UUID tenantId, LocalDate date, Position position, Instant now) {
        return jdbc.sql("""
                        INSERT INTO core.cash_position (tenant_id, business_date, cash_point_id, cash_point_type,
                            branch_id, currency, ledger_balance, counted_balance, teller_session_id, counted_at,
                            difference, status, created_at)
                        VALUES (:tenantId, :date, :cashPointId, :type, :branchId, :currency, :ledgerBalance,
                            :countedBalance, :sessionId, :countedAt, :difference, :status, :now)
                        ON CONFLICT (tenant_id, business_date, cash_point_id) DO NOTHING""")
                .param("tenantId", uuid(tenantId))
                .param("date", date(date))
                .param("cashPointId", uuid(position.cashPointId()))
                .param("type", position.cashPointType())
                .param("branchId", uuid(position.branchId()))
                .param("currency", position.currency())
                .param("ledgerBalance", position.ledgerBalance())
                .param("countedBalance", new SqlParameterValue(Types.NUMERIC, position.countedBalance()))
                .param("sessionId", uuid(position.tellerSessionId()))
                .param("countedAt", new SqlParameterValue(Types.TIMESTAMP,
                        position.countedAt() == null ? null : Timestamp.from(position.countedAt())))
                .param("difference", new SqlParameterValue(Types.NUMERIC, position.difference()))
                .param("status", position.status())
                .param("now", Timestamp.from(now))
                .update() == 1;
    }

    public List<Position> forDate(UUID tenantId, LocalDate date) {
        return jdbc.sql("SELECT * FROM core.cash_position WHERE tenant_id = :tenantId AND business_date = :date"
                        + " ORDER BY branch_id, cash_point_type DESC, currency, cash_point_id")
                .param("tenantId", uuid(tenantId))
                .param("date", date(date))
                .query(ROW_MAPPER)
                .list();
    }

    public Optional<LocalDate> latestDate(UUID tenantId) {
        return jdbc.sql("SELECT max(business_date) FROM core.cash_position WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> Optional.ofNullable(rs.getObject(1, LocalDate.class)))
                .single();
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }

    private static SqlParameterValue date(LocalDate value) {
        return new SqlParameterValue(Types.DATE, Date.valueOf(value));
    }
}
