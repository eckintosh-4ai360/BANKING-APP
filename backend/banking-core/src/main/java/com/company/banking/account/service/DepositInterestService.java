package com.company.banking.account.service;

import com.company.banking.account.entity.Account;
import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.repository.AccountRepository;
import com.company.banking.account.repository.DepositInterestRepository;
import com.company.banking.account.repository.DepositInterestRepository.Accrual;
import com.company.banking.account.repository.DepositInterestRepository.Position;
import com.company.banking.account.repository.DepositInterestRepository.UnpostedGroup;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.id.References;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.service.ProductService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Deposit interest at end-of-day, for the business date being closed ({@code D}) and every calendar day up to the
 * next business date (weekends and holidays accrue on D's closing balance).
 *
 * <ol>
 *   <li><b>Accrual:</b> each day's interest is computed exactly (8 decimals) and added to the account's cumulative
 *   total. For the daily-balance methods the change of the rounded cumulative total is posted to the GL (interest
 *   expense / interest payable) on D, so an account's share of interest payable is always
 *   {@code round(accrued) - paid} and never drifts.</li>
 *   <li><b>Payout:</b> at the end of each interest period the account is credited
 *   {@code round(accrued to the period end) - paid} from interest payable. Sub-unit remainders stay in the cumulative
 *   total and are paid once they add up. Minimum-balance products pay the interest on the period's lowest closing
 *   balance straight from interest expense.</li>
 * </ol>
 * Interest is credited at the end of its period, so days after a period end that are closed in the same run (a month
 * ending on a Saturday) accrue on the balance including that interest. Everything is keyed by (account, day) and
 * (account, period end), so running a step again skips what was done.
 */
@Service
@RequiredArgsConstructor
public class DepositInterestService {

    static final String ACCRUAL_STEP = "DEPOSIT_INTEREST_ACCRUAL";
    static final String PAYOUT_STEP = "DEPOSIT_INTEREST_PAYOUT";
    private static final int BATCH = 200;
    private static final String MINIMUM_BALANCE = "MIN_MONTHLY_BALANCE";
    private static final UUID FIRST = new UUID(0, 0);

    private final AccountRepository accounts;
    private final DepositInterestRepository interest;
    private final ProductService productService;
    private final LedgerAccountService ledgerAccounts;
    private final PostingEngine postingEngine;
    private final CurrencyService currencies;
    private final Clock clock;

    public Map<String, Object> accrue(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> versions = context.inTransaction(productService::interestBearingVersionIds);
        int accrued = 0;
        if (!versions.isEmpty()) {
            UUID after = FIRST;
            while (true) {
                UUID cursor = after;
                List<UUID> batch = context.inTransaction(() -> accounts.interestCandidates(tenantId, versions,
                        AccountStatus.CLOSED, cursor, Limit.of(BATCH)));
                if (batch.isEmpty()) {
                    break;
                }
                context.inTransaction(() -> {
                    Map<UUID, ProductTerms> terms = new HashMap<>();
                    batch.forEach(accountId -> accrueAccount(tenantId, accountId, context, terms));
                    return null;
                });
                accrued += batch.size();
                after = batch.getLast();
                context.checkpoint(ACCRUAL_STEP, "batch");
            }
        }
        int journals = 0;
        for (UnpostedGroup group : context.inTransaction(() -> interest.unpostedGroups(tenantId,
                context.businessDate()))) {
            boolean posted = context.inTransaction(() -> postAccrual(tenantId, context.businessDate(), group));
            journals += posted ? 1 : 0;
            context.checkpoint(ACCRUAL_STEP, "journal");
        }
        return Map.of("accounts", accrued, "journals", journals);
    }

    public Map<String, Object> payOut(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        List<UUID> versions = context.inTransaction(productService::interestBearingVersionIds);
        int paid = 0;
        if (versions.isEmpty()) {
            return Map.of("payouts", 0);
        }
        UUID after = FIRST;
        while (true) {
            UUID cursor = after;
            List<UUID> batch = context.inTransaction(() -> accounts.interestCandidates(tenantId, versions,
                    AccountStatus.CLOSED, cursor, Limit.of(BATCH)));
            if (batch.isEmpty()) {
                break;
            }
            paid += context.inTransaction(() -> {
                Map<UUID, ProductTerms> terms = new HashMap<>();
                int count = 0;
                for (UUID accountId : batch) {
                    count += payOutAccount(tenantId, accountId, context, terms);
                }
                return count;
            });
            after = batch.getLast();
            context.checkpoint(PAYOUT_STEP, "batch");
        }
        return Map.of("payouts", paid);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void accrueAccount(UUID tenantId, UUID accountId, EndOfDayContext context,
                               Map<UUID, ProductTerms> termsCache) {
        LocalDate closed = context.businessDate();
        if (interest.accrualExists(tenantId, accountId, closed)) {
            return;
        }
        Account account = accounts.findByTenantIdAndId(tenantId, accountId).orElseThrow();
        ProductTerms terms = termsCache.computeIfAbsent(account.getProductVersionId(), productService::terms);
        interest.ensurePosition(tenantId, accountId, closed, clock.instant());
        Position position = interest.lockPosition(tenantId, accountId).orElseThrow();
        int minorUnits = currencies.require(account.getCurrency()).minorUnits();
        int yearDays = InterestCalculator.yearDays(terms.dayCount());
        boolean dailyMethod = !minimumBalance(terms);
        LocalDate lastDay = context.nextBusinessDate().minusDays(1);
        List<LocalDate> periodEnds = InterestCalculator.periodEnds(terms.interestPostingFrequency(), closed, lastDay);

        BigDecimal balance = ledgerAccounts.balanceAt(account.getLedgerAccountId(), closed);
        BigDecimal accrued = position.accruedExact();
        Position projected = position;
        for (LocalDate day = closed; !day.isAfter(lastDay); day = day.plusDays(1)) {
            int weight = InterestCalculator.dayWeight(day, terms.dayCount());
            BigDecimal amount = InterestCalculator.dailyInterest(balance, terms.interestRate(), weight, yearDays);
            BigDecimal glAmount = BigDecimal.ZERO;
            if (dailyMethod) {
                BigDecimal next = accrued.add(amount);
                glAmount = InterestCalculator.glIncrement(accrued, next, minorUnits);
                accrued = next;
            }
            interest.insertAccrual(new Accrual(accountId, day, tenantId, closed, account.getBranchId(),
                    terms.interestExpenseGlId(), account.getCurrency(), balance, terms.interestRate(), weight, amount,
                    glAmount));
            if (periodEnds.contains(day) && day.isBefore(lastDay)) {
                // The payout step credits this period's interest on D, so the days after it earn on it too.
                PeriodPayout payout = periodPayout(tenantId, accountId, terms, projected, accrued, day, minorUnits);
                balance = balance.add(payout.amount());
                projected = payout.settled(projected, day);
            }
        }
        interest.recordAccrued(tenantId, accountId, accrued, lastDay, clock.instant());
    }

    /**
     * One GL journal per branch, currency and expense GL for the day's accrual increments.
     */
    private boolean postAccrual(UUID tenantId, LocalDate closed, UnpostedGroup group) {
        BigDecimal total = interest.lockUnpostedTotal(tenantId, closed, group);
        if (total.signum() <= 0) {
            return false;
        }
        String narration = "Deposit interest accrued " + closed;
        PostedJournal journal = postingEngine.postForClosedDate(new PostingRequest(JournalSource.EOD,
                References.next("IA", closed), null, group.branchId(), null, narration, null, List.of(
                PostingLine.toGl(group.expenseGlId(), group.branchId(), group.currency(), EntryDirection.DEBIT,
                        total, narration),
                PostingLine.toSystemAccount(SystemAccount.INTEREST_PAYABLE, group.branchId(), group.currency(),
                        EntryDirection.CREDIT, total, narration))), closed);
        interest.markPosted(tenantId, closed, group, journal.id());
        return true;
    }

    /**
     * Pays every interest period of the account that ends within the days this run closed.
     *
     * @return payouts made
     */
    private int payOutAccount(UUID tenantId, UUID accountId, EndOfDayContext context,
                              Map<UUID, ProductTerms> termsCache) {
        Account account = accounts.findByTenantIdAndId(tenantId, accountId).orElseThrow();
        ProductTerms terms = termsCache.computeIfAbsent(account.getProductVersionId(), productService::terms);
        LocalDate lastDay = context.nextBusinessDate().minusDays(1);
        List<LocalDate> ends = InterestCalculator.periodEnds(terms.interestPostingFrequency(), context.businessDate(),
                lastDay);
        int count = 0;
        for (LocalDate end : ends) {
            if (interest.payoutExists(tenantId, accountId, end)) {
                continue;
            }
            Position position = interest.lockPosition(tenantId, accountId).orElse(null);
            if (position == null) {
                return count; // has never earned interest
            }
            int minorUnits = currencies.require(account.getCurrency()).minorUnits();
            // The cumulative total may already hold days after the period end (closed in the same run).
            BigDecimal accruedAtEnd = position.accruedExact()
                    .subtract(interest.accruedBetween(tenantId, accountId, end.plusDays(1), lastDay));
            PeriodPayout payout = periodPayout(tenantId, accountId, terms, position, accruedAtEnd, end, minorUnits);
            UUID journalId = null;
            if (payout.amount().signum() > 0) {
                String narration = "Interest " + position.periodStart() + " to " + end;
                PostingLine debit = payout.fromPayable()
                        ? PostingLine.toSystemAccount(SystemAccount.INTEREST_PAYABLE, account.getBranchId(),
                        account.getCurrency(), EntryDirection.DEBIT, payout.amount(), narration)
                        : PostingLine.toGl(terms.interestExpenseGlId(), account.getBranchId(), account.getCurrency(),
                        EntryDirection.DEBIT, payout.amount(), narration);
                journalId = postingEngine.postForClosedDate(new PostingRequest(JournalSource.EOD,
                        References.next("IP", context.businessDate()), null, account.getBranchId(), null,
                        narration + " " + account.getAccountNumber(), null, List.of(debit,
                        PostingLine.toLedgerAccount(account.getLedgerAccountId(), EntryDirection.CREDIT,
                                payout.amount(), narration))), context.businessDate()).id();
                count++;
            }
            interest.insertPayout(tenantId, accountId, position.periodStart(), end, payout.amount(), payout.exact(),
                    journalId, clock.instant());
            Position settled = payout.settled(position, end);
            interest.settle(tenantId, accountId, settled.settledExact(), settled.paidOut(), settled.periodStart(),
                    clock.instant());
        }
        return count;
    }

    /**
     * What an interest period ending on {@code end} pays. Daily-balance methods pay
     * {@code round(accrued to the end) - paid so far} from interest payable. The minimum-balance method pays the
     * interest on the period's lowest closing balance, rounded, from interest expense. The accrual step uses this
     * too, so both steps always agree on the amount.
     *
     * @param accruedAtEnd the cumulative exact accrual as of {@code end}
     */
    private PeriodPayout periodPayout(UUID tenantId, UUID accountId, ProductTerms terms, Position position,
                                      BigDecimal accruedAtEnd, LocalDate end, int minorUnits) {
        if (minimumBalance(terms)) {
            BigDecimal exact = interest.periodStats(tenantId, accountId, position.periodStart(), end)
                    .map(stats -> InterestCalculator.minimumBalanceInterest(stats.minimumBalance(),
                            terms.interestRate(), stats.weightedDays(), InterestCalculator.yearDays(terms.dayCount())))
                    .orElse(BigDecimal.ZERO);
            return new PeriodPayout(InterestCalculator.toMinorUnits(exact, minorUnits), exact, false,
                    position.settledExact());
        }
        BigDecimal amount = InterestCalculator.toMinorUnits(accruedAtEnd, minorUnits).subtract(position.paidOut());
        return new PeriodPayout(amount, accruedAtEnd.subtract(position.settledExact()), true, accruedAtEnd);
    }

    private static boolean minimumBalance(ProductTerms terms) {
        return MINIMUM_BALANCE.equals(terms.interestCalcMethod());
    }

    /**
     * @param amount       credited to the account, in minor units
     * @param exact        the period's exact interest
     * @param fromPayable  paid from interest payable (daily-balance methods), not straight from expense
     * @param settledExact the cumulative accrual this payout settles
     */
    private record PeriodPayout(BigDecimal amount, BigDecimal exact, boolean fromPayable, BigDecimal settledExact) {

        Position settled(Position position, LocalDate end) {
            return new Position(position.accruedExact(), settledExact,
                    fromPayable ? position.paidOut().add(amount) : position.paidOut(), end.plusDays(1),
                    position.lastAccrualDate());
        }
    }
}
