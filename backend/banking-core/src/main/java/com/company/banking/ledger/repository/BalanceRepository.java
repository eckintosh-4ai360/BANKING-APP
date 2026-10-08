package com.company.banking.ledger.repository;

import static com.company.banking.ledger.repository.SqlParams.decimal;
import static com.company.banking.ledger.repository.SqlParams.uuid;
import static com.company.banking.ledger.repository.SqlParams.uuidArray;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Reads and locks balance projections. The balances themselves are written only by database triggers.
 */
@Repository
@RequiredArgsConstructor
public class BalanceRepository {

    public record BalanceRow(UUID ledgerAccountId, String currency, String normalSide, boolean balanceCheck,
                             BigDecimal ledgerBalance, BigDecimal holdAmount, BigDecimal availableBalance,
                             BigDecimal overdraftLimit) {
    }

    private static final String SELECT = "SELECT ledger_account_id, currency, normal_side, balance_check,"
            + " ledger_balance, hold_amount, available_balance, overdraft_limit FROM core.account_balance"
            + " WHERE tenant_id = :tenantId AND ledger_account_id = ANY (CAST(:ids AS uuid[]))";

    private static final RowMapper<BalanceRow> MAPPER = (rs, rowNum) -> new BalanceRow(
            rs.getObject("ledger_account_id", UUID.class),
            rs.getString("currency"),
            rs.getString("normal_side"),
            rs.getBoolean("balance_check"),
            rs.getBigDecimal("ledger_balance"),
            rs.getBigDecimal("hold_amount"),
            rs.getBigDecimal("available_balance"),
            rs.getBigDecimal("overdraft_limit"));

    private final JdbcClient jdbc;

    /**
     * Locks the rows in ascending id order, so two postings touching the same accounts always lock them in the
     * same order and cannot deadlock.
     */
    public List<BalanceRow> lockForUpdate(UUID tenantId, Collection<UUID> ledgerAccountIds) {
        if (ledgerAccountIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + " ORDER BY ledger_account_id FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .param("ids", uuidArray(ledgerAccountIds))
                .query(MAPPER)
                .list();
    }

    public List<BalanceRow> find(UUID tenantId, Collection<UUID> ledgerAccountIds) {
        if (ledgerAccountIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT)
                .param("tenantId", uuid(tenantId))
                .param("ids", uuidArray(ledgerAccountIds))
                .query(MAPPER)
                .list();
    }

    public Optional<BalanceRow> find(UUID tenantId, UUID ledgerAccountId) {
        return find(tenantId, List.of(ledgerAccountId)).stream().findFirst();
    }

    public void setOverdraftLimit(UUID tenantId, UUID ledgerAccountId, BigDecimal limit) {
        jdbc.sql("UPDATE core.account_balance SET overdraft_limit = :limit"
                        + " WHERE tenant_id = :tenantId AND ledger_account_id = :id")
                .param("tenantId", uuid(tenantId))
                .param("id", uuid(ledgerAccountId))
                .param("limit", decimal(limit))
                .update();
    }
}
