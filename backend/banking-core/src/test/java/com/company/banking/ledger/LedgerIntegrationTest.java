package com.company.banking.ledger;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ErrorCode;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.AccountingPeriodService;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.ledger.service.LedgerQueryService;
import com.company.banking.ledger.service.PostingEngine;
import com.company.banking.support.IntegrationTest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Helpers for ledger tests: run code as the system actor inside a tenant transaction, open accounts and post the
 * usual journals.
 */
public abstract class LedgerIntegrationTest extends IntegrationTest {

    @Autowired
    protected PostingEngine postingEngine;

    @Autowired
    protected LedgerAccountService ledgerAccounts;

    @Autowired
    protected LedgerQueryService ledgerQueries;

    @Autowired
    protected AccountingPeriodService periods;

    @Autowired
    protected ChartOfAccountService chartOfAccounts;

    @Autowired
    protected PlatformTransactionManager transactionManager;

    /**
     * Runs {@code action} in its own transaction, bound to the tenant, as the system actor.
     */
    protected <T> T inTenant(UUID tenantId, Supplier<T> action) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        return TenantContext.callAs(tenantId, () -> CurrentActor.callAsSystem(tenantId,
                () -> transaction.execute(status -> action.get())));
    }

    protected UUID openDepositAccount(UUID tenantId, UUID branchId, String currency, String name) {
        return inTenant(tenantId, () -> ledgerAccounts.open(new OpenLedgerAccountCommand(null,
                SystemAccount.SAVINGS_DEPOSITS, branchId, currency, LedgerAccountType.CUSTOMER_DEPOSIT, true, name,
                null, null)).id());
    }

    /**
     * Cash received at {@code cashBranch} and credited to a deposit account.
     */
    protected PostedJournal deposit(UUID tenantId, UUID account, UUID cashBranch, String currency, String amount) {
        return post(tenantId, cashBranch, List.of(
                PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, cashBranch, currency, EntryDirection.DEBIT,
                        money(amount), "Cash deposit"),
                PostingLine.toLedgerAccount(account, EntryDirection.CREDIT, money(amount), "Cash deposit")));
    }

    protected PostedJournal withdraw(UUID tenantId, UUID account, UUID cashBranch, String currency, String amount) {
        return post(tenantId, cashBranch, List.of(
                PostingLine.toLedgerAccount(account, EntryDirection.DEBIT, money(amount), "Cash withdrawal"),
                PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, cashBranch, currency, EntryDirection.CREDIT,
                        money(amount), "Cash withdrawal")));
    }

    protected PostedJournal transfer(UUID tenantId, UUID branchId, UUID from, UUID to, String amount) {
        return post(tenantId, branchId, List.of(
                PostingLine.toLedgerAccount(from, EntryDirection.DEBIT, money(amount), "Transfer out"),
                PostingLine.toLedgerAccount(to, EntryDirection.CREDIT, money(amount), "Transfer in")));
    }

    protected PostedJournal post(UUID tenantId, UUID originBranch, List<PostingLine> lines) {
        return inTenant(tenantId, () -> postingEngine.post(new PostingRequest(JournalSource.TRANSACTION,
                "TEST-" + UUID.randomUUID().toString().substring(0, 8), null, originBranch, null, "Test posting",
                null, lines)));
    }

    protected BigDecimal balanceOf(UUID tenantId, UUID account) {
        return inTenant(tenantId, () -> ledgerAccounts.balance(account).ledgerBalance());
    }

    protected static BigDecimal money(String amount) {
        return new BigDecimal(amount);
    }

    protected static void assertFailsWith(ErrorCode code, ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(BankingException.class,
                error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode()).isEqualTo(code));
    }
}
