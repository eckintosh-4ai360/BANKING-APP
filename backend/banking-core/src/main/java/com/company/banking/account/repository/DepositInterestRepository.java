package com.company.banking.account.repository;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Interest positions, daily accruals and payouts (all written by end-of-day).
 */
@Repository
@RequiredArgsConstructor
public class DepositInterestRepository {

    /**
     * An account's interest position.
     *
     * @param accruedExact exact interest accrued so far (daily-balance methods); never decreases
     * @param settledExact {@code accruedExact} as of the last period end paid
     * @param paidOut      interest paid from interest payable so far
     * @param periodStart  first day of the period not yet paid
     */
    public record Position(BigDecimal accruedExact, BigDecimal settledExact, BigDecimal paidOut,
                           LocalDate periodStart, LocalDate lastAccrualDate) {
    }

    public record Accrual(UUID accountId, LocalDate accrualDate, UUID tenantId, LocalDate businessDate,
                          UUID branchId, UUID expenseGlId, String currency, BigDecimal balance, BigDecimal rate,
                          int dayWeight, BigDecimal amount, BigDecimal glAmount) {
    }

    /** Accruals of one closed date still to post to the GL, per branch, currency and expense GL. */
    public record UnpostedGroup(UUID branchId, String currency, UUID expenseGlId, BigDecimal total) {
    }

    public record PeriodStats(BigDecimal minimumBalance, int weightedDays) {
    }

    private final JdbcClient jdbc;

    public void ensurePosition(UUID tenantId, UUID accountId, LocalDate periodStart, Instant now) {
        jdbc.sql("INSERT INTO core.deposit_interest_position (account_id, tenant_id, period_start, updated_at)"
                        + " VALUES (:accountId, :tenantId, :periodStart, :now) ON CONFLICT (account_id) DO NOTHING")
                .param("accountId", uuid(accountId))
                .param("tenantId", uuid(tenantId))
                .param("periodStart", date(periodStart))
                .param("now", Timestamp.from(now))
                .update();
    }

    /**
     * Row lock: one end-of-day worker updates an account's position at a time. Empty when the account has never
     * earned interest.
     */
    public Optional<Position> lockPosition(UUID tenantId, UUID accountId) {
        return jdbc.sql("SELECT accrued_exact, settled_exact, paid_out, period_start, last_accrual_date"
                        + " FROM core.deposit_interest_position"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .query((rs, rowNum) -> new Position(rs.getBigDecimal("accrued_exact"),
                        rs.getBigDecimal("settled_exact"), rs.getBigDecimal("paid_out"),
                        rs.getObject("period_start", LocalDate.class),
                        rs.getObject("last_accrual_date", LocalDate.class)))
                .optional();
    }

    public void recordAccrued(UUID tenantId, UUID accountId, BigDecimal accruedExact, LocalDate lastAccrualDate,
                              Instant now) {
        jdbc.sql("UPDATE core.deposit_interest_position SET accrued_exact = :accrued,"
                        + " last_accrual_date = :lastAccrual, updated_at = :now"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId")
                .param("accrued", new SqlParameterValue(Types.NUMERIC, accruedExact))
                .param("lastAccrual", date(lastAccrualDate))
                .param("now", Timestamp.from(now))
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .update();
    }

    /**
     * Closes an interest period: what was accrued up to its end is settled, the amount paid is added, and the next
     * period starts.
     */
    public void settle(UUID tenantId, UUID accountId, BigDecimal settledExact, BigDecimal paidOut,
                       LocalDate nextPeriodStart, Instant now) {
        jdbc.sql("UPDATE core.deposit_interest_position SET settled_exact = :settled, paid_out = :paid,"
                        + " period_start = :periodStart, updated_at = :now"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId")
                .param("settled", new SqlParameterValue(Types.NUMERIC, settledExact))
                .param("paid", new SqlParameterValue(Types.NUMERIC, paidOut))
                .param("periodStart", date(nextPeriodStart))
                .param("now", Timestamp.from(now))
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .update();
    }

    public boolean accrualExists(UUID tenantId, UUID accountId, LocalDate accrualDate) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.deposit_interest_accrual"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId AND accrual_date = :date)")
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .param("date", date(accrualDate))
                .query(Boolean.class)
                .single();
    }

    public void insertAccrual(Accrual accrual) {
        jdbc.sql("""
                        INSERT INTO core.deposit_interest_accrual (account_id, accrual_date, tenant_id, business_date,
                                                                   branch_id, expense_gl_id, currency, balance, rate,
                                                                   day_weight, amount, gl_amount)
                        VALUES (:accountId, :accrualDate, :tenantId, :businessDate, :branchId, :expenseGlId,
                                :currency, :balance, :rate, :dayWeight, :amount, :glAmount)""")
                .param("accountId", uuid(accrual.accountId()))
                .param("accrualDate", date(accrual.accrualDate()))
                .param("tenantId", uuid(accrual.tenantId()))
                .param("businessDate", date(accrual.businessDate()))
                .param("branchId", uuid(accrual.branchId()))
                .param("expenseGlId", uuid(accrual.expenseGlId()))
                .param("currency", accrual.currency())
                .param("balance", accrual.balance())
                .param("rate", accrual.rate())
                .param("dayWeight", accrual.dayWeight())
                .param("amount", accrual.amount())
                .param("glAmount", accrual.glAmount())
                .update();
    }

    public List<UnpostedGroup> unpostedGroups(UUID tenantId, LocalDate businessDate) {
        return jdbc.sql("SELECT branch_id, currency, expense_gl_id, sum(gl_amount) AS total"
                        + " FROM core.deposit_interest_accrual"
                        + " WHERE tenant_id = :tenantId AND business_date = :date"
                        + " AND journal_entry_id IS NULL AND gl_amount > 0"
                        + " GROUP BY branch_id, currency, expense_gl_id HAVING sum(gl_amount) > 0"
                        + " ORDER BY branch_id, currency, expense_gl_id")
                .param("tenantId", uuid(tenantId))
                .param("date", date(businessDate))
                .query((rs, rowNum) -> new UnpostedGroup(rs.getObject("branch_id", UUID.class),
                        rs.getString("currency"), rs.getObject("expense_gl_id", UUID.class),
                        rs.getBigDecimal("total")))
                .list();
    }

    /**
     * Locks a group's unposted accruals and returns their total (zero when another run already posted them).
     */
    public BigDecimal lockUnpostedTotal(UUID tenantId, LocalDate businessDate, UnpostedGroup group) {
        List<BigDecimal> amounts = jdbc.sql("SELECT gl_amount FROM core.deposit_interest_accrual"
                        + " WHERE tenant_id = :tenantId AND business_date = :date"
                        + " AND journal_entry_id IS NULL AND gl_amount > 0"
                        + " AND branch_id = :branchId AND currency = :currency AND expense_gl_id = :glId"
                        + " FOR UPDATE")
                .param("tenantId", uuid(tenantId))
                .param("date", date(businessDate))
                .param("branchId", uuid(group.branchId()))
                .param("currency", group.currency())
                .param("glId", uuid(group.expenseGlId()))
                .query(BigDecimal.class)
                .list();
        return amounts.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void markPosted(UUID tenantId, LocalDate businessDate, UnpostedGroup group, UUID journalId) {
        jdbc.sql("UPDATE core.deposit_interest_accrual SET journal_entry_id = :journalId"
                        + " WHERE tenant_id = :tenantId AND business_date = :date"
                        + " AND journal_entry_id IS NULL AND gl_amount > 0"
                        + " AND branch_id = :branchId AND currency = :currency AND expense_gl_id = :glId")
                .param("journalId", uuid(journalId))
                .param("tenantId", uuid(tenantId))
                .param("date", date(businessDate))
                .param("branchId", uuid(group.branchId()))
                .param("currency", group.currency())
                .param("glId", uuid(group.expenseGlId()))
                .update();
    }

    /**
     * Sum of exact accruals of an account in {@code [from, to]}.
     */
    public BigDecimal accruedBetween(UUID tenantId, UUID accountId, LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT coalesce(sum(amount), 0) FROM core.deposit_interest_accrual"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId"
                        + " AND accrual_date BETWEEN :from AND :to")
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .param("from", date(from))
                .param("to", date(to))
                .query(BigDecimal.class)
                .single();
    }

    public Optional<PeriodStats> periodStats(UUID tenantId, UUID accountId, LocalDate from, LocalDate to) {
        return jdbc.sql("SELECT min(balance) AS minimum, coalesce(sum(day_weight), 0) AS weights, count(*) AS days"
                        + " FROM core.deposit_interest_accrual"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId"
                        + " AND accrual_date BETWEEN :from AND :to")
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .param("from", date(from))
                .param("to", date(to))
                .query((rs, rowNum) -> rs.getLong("days") == 0 ? null
                        : new PeriodStats(rs.getBigDecimal("minimum"), rs.getInt("weights")))
                .optional();
    }

    public boolean payoutExists(UUID tenantId, UUID accountId, LocalDate periodEnd) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM core.deposit_interest_payout"
                        + " WHERE tenant_id = :tenantId AND account_id = :accountId AND period_end = :periodEnd)")
                .param("tenantId", uuid(tenantId))
                .param("accountId", uuid(accountId))
                .param("periodEnd", date(periodEnd))
                .query(Boolean.class)
                .single();
    }

    @SuppressWarnings("java:S107")
    public void insertPayout(UUID tenantId, UUID accountId, LocalDate periodStart, LocalDate periodEnd,
                             BigDecimal amount, BigDecimal exactAmount, UUID journalId, Instant now) {
        jdbc.sql("INSERT INTO core.deposit_interest_payout (account_id, period_end, tenant_id, period_start, amount,"
                        + " exact_amount, journal_entry_id, created_at)"
                        + " VALUES (:accountId, :periodEnd, :tenantId, :periodStart, :amount, :exact, :journalId,"
                        + " :now)")
                .param("accountId", uuid(accountId))
                .param("periodEnd", date(periodEnd))
                .param("tenantId", uuid(tenantId))
                .param("periodStart", date(periodStart))
                .param("amount", amount)
                .param("exact", exactAmount)
                .param("journalId", uuid(journalId))
                .param("now", Timestamp.from(now))
                .update();
    }

    private static SqlParameterValue uuid(UUID value) {
        return new SqlParameterValue(Types.OTHER, value);
    }

    private static SqlParameterValue date(LocalDate value) {
        return new SqlParameterValue(Types.DATE, value == null ? null : Date.valueOf(value));
    }
}
