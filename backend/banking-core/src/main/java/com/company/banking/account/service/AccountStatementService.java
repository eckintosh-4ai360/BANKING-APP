package com.company.banking.account.service;

import com.company.banking.account.dto.AccountStatement;
import com.company.banking.account.entity.Account;
import com.company.banking.account.entity.AccountHolder;
import com.company.banking.account.repository.AccountHolderRepository;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.ledger.dto.LedgerAccountStatement;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.product.dto.ProductRef;
import com.company.banking.product.service.ProductService;
import com.company.banking.tenant.service.TenantService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account statements from the ledger, as data (JSON) or as a CSV or PDF download. Statements carry personal and
 * financial data, so every download is audited.
 */
@Service
@RequiredArgsConstructor
public class AccountStatementService {

    public enum Format {
        CSV("text/csv", "csv"),
        PDF("application/pdf", "pdf");

        private final String contentType;
        private final String extension;

        Format(String contentType, String extension) {
            this.contentType = contentType;
            this.extension = extension;
        }

        public String contentType() {
            return contentType;
        }
    }

    /**
     * A rendered statement ready to download.
     */
    public record StatementFile(String fileName, String contentType, byte[] content) {
    }

    static final int MAX_DAYS = 366;
    static final int DEFAULT_DAYS = 30;
    static final int MAX_LINES = 10_000;

    private final AccountAccessGuard accessGuard;
    private final AccountHolderRepository holderRepository;
    private final LedgerAccountService ledgerAccounts;
    private final ProductService productService;
    private final CustomerService customerService;
    private final BranchService branchService;
    private final TenantService tenantService;
    private final BusinessDateService businessDates;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * @param from defaults to 30 days before {@code to}
     * @param to   defaults to the current business date
     */
    @Transactional(readOnly = true)
    public AccountStatement statement(UUID accountId, LocalDate from, LocalDate to) {
        Account account = accessGuard.loadForRead(accountId);
        LocalDate end = to != null ? to : businessDates.today();
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
        if (start.isAfter(end) || start.plusDays(MAX_DAYS).isBefore(end)) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "The period must run forwards and cover at most a year.");
        }
        LedgerAccountStatement ledger = ledgerAccounts.statement(account.getLedgerAccountId(), start, end, MAX_LINES);
        List<AccountHolder> holders = holderRepository.findCurrent(account.getTenantId(), accountId).stream()
                .sorted(Comparator.comparing(AccountHolder::getRole))
                .toList();
        Map<UUID, CustomerSummary> customers = customerService.summaries(holders.stream()
                .map(AccountHolder::getCustomerId).toList());
        ProductRef product = productService.refs(List.of(account.getProductId())).get(account.getProductId());
        return new AccountStatement(tenantService.getCurrent().displayName(), account.getId(),
                account.getAccountNumber(), account.getTitle(),
                holders.stream().map(holder -> customers.get(holder.getCustomerId()))
                        .filter(Objects::nonNull)
                        .map(CustomerSummary::displayName)
                        .toList(),
                product.name(), branchService.getForInternalUse(account.getBranchId()).name(), ledger.currency(),
                start, end, ledger.openingBalance(), ledger.totalDebits(), ledger.totalCredits(),
                ledger.closingBalance(), clock.instant(), ledger.lines().stream()
                .map(line -> new AccountStatement.Line(line.businessDate(), line.valueDate(), line.reference(),
                        line.narration(), line.debit(), line.credit(), line.balance()))
                .toList());
    }

    /**
     * Renders the statement for download and records who downloaded which account and period.
     */
    @Transactional
    public StatementFile export(UUID accountId, LocalDate from, LocalDate to, Format format) {
        AccountStatement statement = statement(accountId, from, to);
        byte[] content = format == Format.PDF ? StatementPdfWriter.write(statement)
                : StatementCsvWriter.write(statement);
        auditService.record(AuditEvent.builder("ACCOUNT_STATEMENT_EXPORTED", AccountService.RESOURCE)
                .resourceId(accountId)
                .resourceReference(statement.accountNumber())
                .metadata("format", format)
                .metadata("from", statement.from().toString())
                .metadata("to", statement.to().toString())
                .metadata("lines", statement.lines().size())
                .build());
        return new StatementFile("statement-" + statement.accountNumber() + "-" + statement.from() + "-"
                + statement.to() + "." + format.extension, format.contentType(), content);
    }
}
