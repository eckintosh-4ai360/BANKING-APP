package com.company.banking.ledger.service;

import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.ChartOfAccountResponse;
import com.company.banking.ledger.dto.JournalLineResponse;
import com.company.banking.ledger.dto.JournalResponse;
import com.company.banking.ledger.dto.JournalSearchCriteria;
import com.company.banking.ledger.dto.JournalSummary;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.ledger.dto.TrialBalanceResponse;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.repository.JournalRepository;
import com.company.banking.ledger.repository.JournalRepository.JournalRow;
import com.company.banking.ledger.repository.JournalRepository.LineView;
import com.company.banking.ledger.repository.LedgerReportRepository;
import com.company.banking.ledger.repository.LedgerReportRepository.GlTotals;
import com.company.banking.tenant.service.TenantService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side of the ledger: journals, trial balance and the integrity check. Every figure comes from the entries
 * themselves. Staff limited to some branches see only those branches.
 */
@Service
@RequiredArgsConstructor
public class LedgerQueryService {

    private final JournalRepository journals;
    private final LedgerReportRepository reports;
    private final ChartOfAccountService chartOfAccounts;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final TenantService tenantService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public JournalResponse journal(UUID journalId) {
        UUID tenantId = TenantContext.requireTenantId();
        JournalRow journal = journals.find(tenantId, journalId)
                .orElseThrow(() -> new ResourceNotFoundException("Journal"));
        List<LineView> lines = journals.findLines(tenantId, journalId);
        BranchScope scope = scope();
        if (!scope.permits(journal.branchId()) && lines.stream().noneMatch(line -> scope.permits(line.branchId()))) {
            throw new ResourceNotFoundException("Journal");
        }
        return new JournalResponse(journal.id(), journal.journalNumber(), journal.businessDate(), journal.valueDate(),
                journal.postedAt(), journal.sourceType(), journal.sourceReference(), journal.financialTransactionId(),
                journal.reversesJournalId(), journals.findReversalOf(tenantId, journalId).orElse(null),
                journal.branchId(), journal.postedBy(), journal.approvedBy(), journal.description(),
                lines.stream().map(this::toLine).toList());
    }

    /**
     * Journals produced by one financial transaction (the posting and any reversal).
     */
    @Transactional(readOnly = true)
    public List<JournalResponse> journalsOfTransaction(UUID financialTransactionId) {
        return journals.findByTransaction(TenantContext.requireTenantId(), financialTransactionId).stream()
                .map(row -> journal(row.id()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<JournalSummary> search(JournalSearchCriteria criteria, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        Collection<UUID> branches = branchFilter(criteria.branchIds());
        List<JournalSummary> items = journals.search(tenantId, criteria.from(), criteria.to(), criteria.sourceType(),
                        criteria.sourceReference(), branches, page.getPageSize(), page.getOffset()).stream()
                .map(row -> new JournalSummary(row.id(), row.journalNumber(), row.businessDate(), row.postedAt(),
                        row.sourceType(), row.sourceReference(), row.branchId(), row.description(),
                        row.reversesJournalId()))
                .toList();
        long total = journals.count(tenantId, criteria.from(), criteria.to(), criteria.sourceType(),
                criteria.sourceReference(), branches);
        return PageResponse.of(items, page.getPageNumber(), page.getPageSize(), total);
    }

    /**
     * Trial balance in one currency (default: the institution's base currency) as of the end of {@code asOf}
     * (default: today). Header accounts carry the totals of their children.
     */
    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(LocalDate asOf, String currency, UUID branchId) {
        UUID tenantId = TenantContext.requireTenantId();
        String reportCurrency = currency == null ? tenantService.getCurrent().baseCurrency() : currency;
        currencies.require(reportCurrency);
        LocalDate date = asOf == null ? businessDates.today() : asOf;
        Collection<UUID> branches = branchFilter(branchId == null ? null : List.of(branchId));

        List<ChartOfAccountResponse> chart = chartOfAccounts.list();
        Map<UUID, GlTotals> totals = reports.glTotals(tenantId, reportCurrency, date, branches).stream()
                .collect(Collectors.toMap(GlTotals::chartOfAccountId, Function.identity()));
        Map<UUID, List<ChartOfAccountResponse>> children = new HashMap<>();
        chart.forEach(account -> children.computeIfAbsent(account.parentId(), key -> new ArrayList<>()).add(account));

        Map<UUID, BigDecimal[]> rolled = new HashMap<>();
        children.getOrDefault(null, List.of()).forEach(root -> rollUp(root, children, totals, rolled));

        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ChartOfAccountResponse account : chart) {
            BigDecimal[] sums = rolled.getOrDefault(account.id(), new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO});
            boolean active = "ACTIVE".equals(account.status());
            if (!active && sums[0].signum() == 0 && sums[1].signum() == 0) {
                continue;
            }
            BigDecimal net = sums[0].subtract(sums[1]);
            BigDecimal debitBalance = net.signum() > 0 ? net : BigDecimal.ZERO;
            BigDecimal creditBalance = net.signum() < 0 ? net.negate() : BigDecimal.ZERO;
            if (!account.header()) {
                totalDebit = totalDebit.add(debitBalance);
                totalCredit = totalCredit.add(creditBalance);
            }
            rows.add(new TrialBalanceRow(account.id(), account.parentId(), account.code(), account.name(),
                    account.accountClass(), account.header(), depth(account, chart),
                    currencies.present(sums[0], reportCurrency), currencies.present(sums[1], reportCurrency),
                    currencies.present(debitBalance, reportCurrency), currencies.present(creditBalance, reportCurrency)));
        }
        return new TrialBalanceResponse(date, reportCurrency, branchId, rows,
                currencies.present(totalDebit, reportCurrency), currencies.present(totalCredit, reportCurrency),
                totalDebit.compareTo(totalCredit) == 0);
    }

    /**
     * Proves the ledger is intact: every balance projection equals the sum of its entries and every journal
     * balances per currency and branch. Any finding is a reconciliation break to investigate.
     */
    @Transactional(readOnly = true)
    public ReconciliationReport reconcile() {
        UUID tenantId = TenantContext.requireTenantId();
        List<ReconciliationReport.BalanceBreak> breaks = reports.balanceBreaks(tenantId).stream()
                .map(check -> new ReconciliationReport.BalanceBreak(check.ledgerAccountId(), check.projected(),
                        check.fromEntries()))
                .toList();
        List<String> unbalanced = reports.unbalancedJournals(tenantId);
        return new ReconciliationReport(clock.instant(), reports.countLedgerAccounts(tenantId),
                reports.countJournals(tenantId), breaks, unbalanced, breaks.isEmpty() && unbalanced.isEmpty());
    }

    private BigDecimal[] rollUp(ChartOfAccountResponse account, Map<UUID, List<ChartOfAccountResponse>> children,
                                Map<UUID, GlTotals> totals, Map<UUID, BigDecimal[]> rolled) {
        GlTotals own = totals.get(account.id());
        BigDecimal debit = own == null ? BigDecimal.ZERO : own.debits();
        BigDecimal credit = own == null ? BigDecimal.ZERO : own.credits();
        for (ChartOfAccountResponse child : children.getOrDefault(account.id(), List.of())) {
            BigDecimal[] childSums = rollUp(child, children, totals, rolled);
            debit = debit.add(childSums[0]);
            credit = credit.add(childSums[1]);
        }
        BigDecimal[] sums = {debit, credit};
        rolled.put(account.id(), sums);
        return sums;
    }

    private static int depth(ChartOfAccountResponse account, List<ChartOfAccountResponse> chart) {
        Map<UUID, UUID> parents = new HashMap<>();
        chart.forEach(item -> parents.put(item.id(), item.parentId()));
        int depth = 0;
        UUID parent = account.parentId();
        while (parent != null && depth < 20) {
            depth++;
            parent = parents.get(parent);
        }
        return depth;
    }

    private JournalLineResponse toLine(LineView line) {
        return new JournalLineResponse(line.lineNo(), line.chartOfAccountId(), line.glCode(), line.glName(),
                line.ledgerAccountId(), line.ledgerAccountName(), line.branchId(), line.currency(),
                EntryDirection.fromCode(line.direction()).name(), currencies.present(line.amount(), line.currency()),
                line.narration());
    }

    private static BranchScope scope() {
        return CurrentActor.current().map(AuthenticatedActor::branchScope).orElse(BranchScope.all());
    }

    /**
     * Requested branches must be in the caller's scope; no request means "everything the caller may see".
     */
    private static Collection<UUID> branchFilter(Collection<UUID> requested) {
        BranchScope scope = scope();
        if (requested != null && !requested.isEmpty()) {
            if (!requested.stream().allMatch(scope::permits)) {
                throw new BankingException(CommonErrorCode.ACCESS_DENIED);
            }
            return requested;
        }
        return scope.allBranches() ? null : scope.branchIds();
    }
}
