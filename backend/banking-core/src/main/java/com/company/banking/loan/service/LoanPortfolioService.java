package com.company.banking.loan.service;

import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.eod.EndOfDayStep;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.entity.Loan;
import com.company.banking.loan.repository.LoanRepository;
import com.company.banking.loan.schedule.LoanArrears;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-of-day for loans ({@code LOAN_PORTFOLIO}, after deposit interest and susu, before the ledger snapshots the
 * day): each active loan is closed for the business date by {@link LoanService#closeDay} (interest, penalties,
 * delinquency band, suspense and provision), in batches that commit on their own; a resumed run skips loans already
 * done. Also the portfolio summary by band.
 */
@Service
@RequiredArgsConstructor
public class LoanPortfolioService implements EndOfDayStep {

    static final String STEP = "LOAN_PORTFOLIO";
    private static final int BATCH = 100;
    private static final int PAGE = 500;
    private static final UUID FIRST = new UUID(0, 0);
    private static final int PAR_DAYS = 30;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final LoanRepository loans;
    private final LoanService loanService;
    private final LoanSettingsService settings;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;

    @Override
    public String code() {
        return STEP;
    }

    @Override
    public int order() {
        return 350;
    }

    @Override
    public Map<String, Object> run(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        LocalDate closed = context.businessDate();
        List<LoanArrears.Band> ladder = context.inTransaction(settings::ladder);
        int[] totals = new int[4]; // processed, reclassified, non-accrual, with penalties
        UUID after = FIRST;
        while (true) {
            UUID cursor = after;
            List<UUID> batch = context.inTransaction(() -> loans.idsWithStatus(tenantId, Loan.Status.ACTIVE, cursor,
                    Limit.of(BATCH)));
            if (batch.isEmpty()) {
                break;
            }
            context.inTransaction(() -> {
                for (UUID loanId : batch) {
                    LoanService.DayResult result = loanService.closeDay(loanId, closed, ladder);
                    if (result != null) {
                        totals[0]++;
                        totals[1] += result.bandChanged() ? 1 : 0;
                        totals[2] += result.nonAccrual() ? 1 : 0;
                        totals[3] += result.penalties().signum() > 0 ? 1 : 0;
                    }
                }
                return null;
            });
            after = batch.getLast();
            context.checkpoint(STEP, "batch");
        }
        return Map.of("loans", totals[0], "reclassified", totals[1], "nonAccrual", totals[2],
                "penalised", totals[3]);
    }

    /**
     * Active loans in the caller's branches by currency and delinquency band, as at the last end-of-day.
     */
    @Transactional(readOnly = true)
    public List<LoanDtos.Portfolio> portfolio() {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Collection<UUID> branches = scope.allBranches() || scope.branchIds().isEmpty()
                ? Set.of(new UUID(0, 0)) : scope.branchIds();
        Map<String, String> bandNames = new LinkedHashMap<>();
        settings.bands().forEach(band -> bandNames.put(band.code(), band.name()));
        Map<String, Totals> byCurrency = new TreeMap<>();
        int page = 0;
        Page<Loan> found;
        do {
            found = loans.search(tenantId, scope.allBranches(), branches, null, Loan.Status.ACTIVE,
                    PageRequest.of(page++, PAGE));
            Map<UUID, BalanceSnapshot> principal = ledgerAccounts.balances(found.getContent().stream()
                    .map(Loan::getPrincipalLedgerAccountId).toList());
            for (Loan loan : found.getContent()) {
                BigDecimal outstanding = principal.get(loan.getPrincipalLedgerAccountId()).ledgerBalance();
                byCurrency.computeIfAbsent(loan.getCurrency(), currency -> new Totals()).add(loan, outstanding);
            }
        } while (found.hasNext());

        List<LoanDtos.Portfolio> portfolios = new ArrayList<>();
        byCurrency.forEach((currency, totals) -> {
            List<LoanDtos.PortfolioBand> bands = new ArrayList<>();
            totals.bands.forEach((code, band) -> bands.add(new LoanDtos.PortfolioBand(code,
                    bandNames.getOrDefault(code, code), band.loans, present(band.principal, currency),
                    present(band.provision, currency))));
            BigDecimal par30Percent = totals.principal.signum() == 0 ? BigDecimal.ZERO
                    : totals.atRisk.multiply(HUNDRED).divide(totals.principal, 2, RoundingMode.HALF_UP);
            portfolios.add(new LoanDtos.Portfolio(currency, totals.loans, present(totals.principal, currency),
                    present(totals.atRisk, currency), par30Percent, present(totals.provision, currency),
                    totals.nonAccrual, bands));
        });
        return portfolios;
    }

    private BigDecimal present(BigDecimal amount, String currency) {
        return currencies.present(amount, currency);
    }

    /** Running totals of one currency (and, inside, of each band). */
    private static final class Totals {

        private long loans;
        private long nonAccrual;
        private BigDecimal principal = BigDecimal.ZERO;
        private BigDecimal atRisk = BigDecimal.ZERO;
        private BigDecimal provision = BigDecimal.ZERO;
        private final Map<String, BandTotals> bands = new LinkedHashMap<>();

        void add(Loan loan, BigDecimal outstanding) {
            loans++;
            nonAccrual += loan.isNonAccrual() ? 1 : 0;
            principal = principal.add(outstanding);
            provision = provision.add(loan.getProvisionHeld());
            if (loan.getDaysPastDue() > PAR_DAYS) {
                atRisk = atRisk.add(outstanding);
            }
            String band = loan.getDelinquencyBand() == null ? "UNCLASSIFIED" : loan.getDelinquencyBand();
            BandTotals totals = bands.computeIfAbsent(band, code -> new BandTotals());
            totals.loans++;
            totals.principal = totals.principal.add(outstanding);
            totals.provision = totals.provision.add(loan.getProvisionHeld());
        }
    }

    private static final class BandTotals {

        private long loans;
        private BigDecimal principal = BigDecimal.ZERO;
        private BigDecimal provision = BigDecimal.ZERO;
    }
}
