package com.company.banking.ledger.repository;

import static com.company.banking.ledger.repository.SqlParams.date;
import static com.company.banking.ledger.repository.SqlParams.uuid;
import static com.company.banking.ledger.repository.SqlParams.uuidArray;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Aggregations straight from the ledger entries (the source of truth), never from projections.
 */
@Repository
@RequiredArgsConstructor
public class LedgerReportRepository {

    public record GlTotals(UUID chartOfAccountId, BigDecimal debits, BigDecimal credits) {
    }

    public record BalanceCheck(UUID ledgerAccountId, BigDecimal projected, BigDecimal fromEntries) {
    }

    /**
     * One entry of a sub-ledger account with its journal details, for statements.
     */
    public record StatementEntry(LocalDate businessDate, LocalDate valueDate, Instant postedAt, String journalNumber,
                                 String sourceType, String sourceReference, String narration, String direction,
                                 BigDecimal amount) {
    }

    private final JdbcClient jdbc;

    /**
     * Writes the closing position of every GL account, branch and currency for a business date, starting from each
     * one's latest earlier snapshot (so a day only reads the entries since then). One statement: it either writes
     * the whole day or nothing, and a second call for the same day writes nothing.
     *
     * @return rows written
     */
    public int snapshotGlBalances(UUID tenantId, LocalDate businessDate) {
        return jdbc.sql("""
                        WITH last AS (
                            SELECT DISTINCT ON (chart_of_account_id, branch_id, currency)
                                   chart_of_account_id, branch_id, currency, business_date AS snapshot_date,
                                   closing_balance
                            FROM core.gl_balance_snapshot
                            WHERE tenant_id = :tenantId AND business_date < :date
                            ORDER BY chart_of_account_id, branch_id, currency, business_date DESC),
                        moves AS (
                            SELECT e.chart_of_account_id, e.branch_id, e.currency,
                                   coalesce(sum(CASE WHEN e.direction = 'D' THEN e.amount ELSE -e.amount END)
                                            FILTER (WHERE e.business_date < :date), 0) AS before_net,
                                   coalesce(sum(e.amount) FILTER (WHERE e.business_date = :date
                                                                    AND e.direction = 'D'), 0) AS debits,
                                   coalesce(sum(e.amount) FILTER (WHERE e.business_date = :date
                                                                    AND e.direction = 'C'), 0) AS credits
                            FROM core.ledger_entry e
                            LEFT JOIN last l ON l.chart_of_account_id = e.chart_of_account_id
                                AND l.branch_id = e.branch_id AND l.currency = e.currency
                            WHERE e.tenant_id = :tenantId AND e.business_date <= :date
                              AND (l.snapshot_date IS NULL OR e.business_date > l.snapshot_date)
                            GROUP BY e.chart_of_account_id, e.branch_id, e.currency),
                        keyed AS (
                            SELECT chart_of_account_id, branch_id, currency FROM last
                            UNION
                            SELECT chart_of_account_id, branch_id, currency FROM moves),
                        computed AS (
                            SELECT k.chart_of_account_id, k.branch_id, k.currency,
                                   coalesce(l.closing_balance, 0) + coalesce(m.before_net, 0) AS opening,
                                   coalesce(m.debits, 0) AS debits, coalesce(m.credits, 0) AS credits
                            FROM keyed k
                            LEFT JOIN last l ON l.chart_of_account_id = k.chart_of_account_id
                                AND l.branch_id = k.branch_id AND l.currency = k.currency
                            LEFT JOIN moves m ON m.chart_of_account_id = k.chart_of_account_id
                                AND m.branch_id = k.branch_id AND m.currency = k.currency)
                        INSERT INTO core.gl_balance_snapshot (tenant_id, business_date, chart_of_account_id, branch_id,
                                                              currency, opening_balance, debits, credits,
                                                              closing_balance)
                        SELECT :tenantId, :date, chart_of_account_id, branch_id, currency, opening, debits, credits,
                               opening + debits - credits
                        FROM computed
                        ON CONFLICT DO NOTHING""")
                .param("tenantId", uuid(tenantId))
                .param("date", date(businessDate))
                .update();
    }

    /**
     * Entries of a sub-ledger account between two business dates, in posting order; at most {@code limit} rows.
     */
    public List<StatementEntry> accountEntries(UUID tenantId, UUID ledgerAccountId, LocalDate from, LocalDate to,
                                               int limit) {
        return jdbc.sql("SELECT e.business_date, j.value_date, j.posted_at, j.journal_number, j.source_type,"
                        + " j.source_reference, coalesce(e.narration, j.description) AS narration, e.direction,"
                        + " e.amount"
                        + " FROM core.ledger_entry e"
                        + " JOIN core.journal_entry j ON j.tenant_id = e.tenant_id AND j.id = e.journal_entry_id"
                        + " WHERE e.tenant_id = :tenantId AND e.ledger_account_id = :ledgerAccountId"
                        + " AND e.business_date BETWEEN :from AND :to"
                        + " ORDER BY e.business_date, j.posted_at, j.journal_number, e.line_no"
                        + " LIMIT :limit")
                .param("tenantId", uuid(tenantId))
                .param("ledgerAccountId", uuid(ledgerAccountId))
                .param("from", date(from))
                .param("to", date(to))
                .param("limit", limit)
                .query((rs, rowNum) -> new StatementEntry(rs.getObject("business_date", LocalDate.class),
                        rs.getObject("value_date", LocalDate.class),
                        rs.getTimestamp("posted_at").toInstant(), rs.getString("journal_number"),
                        rs.getString("source_type"), rs.getString("source_reference"), rs.getString("narration"),
                        rs.getString("direction"), rs.getBigDecimal("amount")))
                .list();
    }

    /**
     * Debit and credit totals of a sub-ledger account before a business date (the opening position of a statement).
     */
    public GlTotals accountTotalsBefore(UUID tenantId, UUID ledgerAccountId, LocalDate before) {
        return jdbc.sql("SELECT coalesce(sum(amount) FILTER (WHERE direction = 'D'), 0) AS debits,"
                        + " coalesce(sum(amount) FILTER (WHERE direction = 'C'), 0) AS credits"
                        + " FROM core.ledger_entry"
                        + " WHERE tenant_id = :tenantId AND ledger_account_id = :ledgerAccountId"
                        + " AND business_date < :before")
                .param("tenantId", uuid(tenantId))
                .param("ledgerAccountId", uuid(ledgerAccountId))
                .param("before", date(before))
                .query((rs, rowNum) -> new GlTotals(null, rs.getBigDecimal("debits"), rs.getBigDecimal("credits")))
                .single();
    }

    /**
     * Debit and credit totals per GL account up to and including {@code asOf}, in one currency.
     */
    public List<GlTotals> glTotals(UUID tenantId, String currency, LocalDate asOf, Collection<UUID> branchIds) {
        return jdbc.sql("SELECT chart_of_account_id,"
                        + " coalesce(sum(amount) FILTER (WHERE direction = 'D'), 0) AS debits,"
                        + " coalesce(sum(amount) FILTER (WHERE direction = 'C'), 0) AS credits"
                        + " FROM core.ledger_entry"
                        + " WHERE tenant_id = :tenantId AND currency = :currency AND business_date <= :asOf"
                        + " AND (CAST(:branchIds AS uuid[]) IS NULL OR branch_id = ANY (CAST(:branchIds AS uuid[])))"
                        + " GROUP BY chart_of_account_id")
                .param("tenantId", uuid(tenantId))
                .param("currency", currency)
                .param("asOf", date(asOf))
                .param("branchIds", uuidArray(branchIds))
                .query((rs, rowNum) -> new GlTotals(rs.getObject("chart_of_account_id", UUID.class),
                        rs.getBigDecimal("debits"), rs.getBigDecimal("credits")))
                .list();
    }

    public long countLedgerAccounts(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM core.account_balance WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .query(Long.class)
                .single();
    }

    public long countJournals(UUID tenantId) {
        return jdbc.sql("SELECT count(*) FROM core.journal_entry WHERE tenant_id = :tenantId")
                .param("tenantId", uuid(tenantId))
                .query(Long.class)
                .single();
    }

    /**
     * Ledger accounts whose projected balance differs from the sum of their entries.
     */
    public List<BalanceCheck> balanceBreaks(UUID tenantId) {
        return jdbc.sql("SELECT ledger_account_id, ledger_balance, from_entries FROM ("
                        + " SELECT b.ledger_account_id, b.ledger_balance,"
                        + " coalesce((SELECT sum(CASE WHEN (e.direction = 'D') = (b.normal_side = 'DEBIT')"
                        + " THEN e.amount ELSE -e.amount END)"
                        + " FROM core.ledger_entry e"
                        + " WHERE e.tenant_id = b.tenant_id AND e.ledger_account_id = b.ledger_account_id), 0)"
                        + " AS from_entries"
                        + " FROM core.account_balance b WHERE b.tenant_id = :tenantId) checked"
                        + " WHERE ledger_balance <> from_entries")
                .param("tenantId", uuid(tenantId))
                .query((rs, rowNum) -> new BalanceCheck(rs.getObject("ledger_account_id", UUID.class),
                        rs.getBigDecimal("ledger_balance"), rs.getBigDecimal("from_entries")))
                .list();
    }

    /**
     * Journal numbers that do not balance per currency and branch, or have fewer than two lines.
     */
    public List<String> unbalancedJournals(UUID tenantId) {
        return jdbc.sql("SELECT j.journal_number FROM core.journal_entry j WHERE j.tenant_id = :tenantId AND ("
                        + " (SELECT count(*) FROM core.ledger_entry e"
                        + " WHERE e.tenant_id = j.tenant_id AND e.journal_entry_id = j.id) < 2"
                        + " OR EXISTS (SELECT 1 FROM core.ledger_entry e"
                        + " WHERE e.tenant_id = j.tenant_id AND e.journal_entry_id = j.id"
                        + " GROUP BY e.currency, e.branch_id"
                        + " HAVING sum(CASE e.direction WHEN 'D' THEN e.amount ELSE -e.amount END) <> 0))"
                        + " ORDER BY j.journal_number LIMIT 100")
                .param("tenantId", uuid(tenantId))
                .query(String.class)
                .list();
    }

    /**
     * Net balance of a GL account across all branches and currencies, in debit-minus-credit terms.
     */
    public BigDecimal glNetBalance(UUID tenantId, UUID chartOfAccountId) {
        return jdbc.sql("SELECT coalesce(sum(CASE direction WHEN 'D' THEN amount ELSE -amount END), 0)"
                        + " FROM core.ledger_entry WHERE tenant_id = :tenantId AND chart_of_account_id = :glId")
                .param("tenantId", uuid(tenantId))
                .param("glId", uuid(chartOfAccountId))
                .query(BigDecimal.class)
                .single();
    }

    public boolean hasActiveLedgerAccounts(UUID tenantId, UUID chartOfAccountId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.ledger_account"
                        + " WHERE tenant_id = :tenantId AND chart_of_account_id = :glId AND status = 'ACTIVE')")
                .param("tenantId", uuid(tenantId))
                .param("glId", uuid(chartOfAccountId))
                .query(Boolean.class)
                .single();
    }
}
