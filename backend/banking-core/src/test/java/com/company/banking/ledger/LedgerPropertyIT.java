package com.company.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.ledger.dto.TrialBalanceResponse;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Property-style tests with fixed seeds (so a failure is reproducible): whatever valid journals are posted, in
 * whatever mix of branches and currencies, the ledger stays balanced and every projection matches its entries.
 */
class LedgerPropertyIT extends LedgerIntegrationTest {

    private static final List<String> CURRENCIES = List.of("GHS", "USD");
    private static final List<SystemAccount> GL_TARGETS = List.of(SystemAccount.CASH_AT_BRANCH,
            SystemAccount.OPERATING_EXPENSE, SystemAccount.OTHER_INCOME, SystemAccount.SUSPENSE_DEBIT,
            SystemAccount.TRANSACTION_FEE_INCOME);

    private TenantHandle tenant;
    private List<UUID> branches;
    private final Map<String, List<UUID>> accountsByCurrency = new HashMap<>();

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        branches = List.of(tenant.headOfficeId(), fixtures.createBranch(tenant, "B2"),
                fixtures.createBranch(tenant, "B3"));
        for (String currency : CURRENCIES) {
            List<UUID> accounts = new ArrayList<>();
            for (UUID branch : branches) {
                for (int index = 0; index < 2; index++) {
                    UUID account = openDepositAccount(tenant.id(), branch, currency, currency + " account " + index);
                    deposit(tenant.id(), account, branch, currency, "1000.00");
                    accounts.add(account);
                }
            }
            accountsByCurrency.put(currency, accounts);
        }
    }

    @Test
    void randomValidJournalsKeepTheLedgerBalancedEverywhere() {
        Random random = new Random(20261008L);
        int posted = 0;
        int refused = 0;
        for (int iteration = 0; iteration < 250; iteration++) {
            String currency = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
            List<PostingLine> lines = randomBalancedLines(random, currency);
            try {
                post(tenant.id(), branches.get(random.nextInt(branches.size())), lines);
                posted++;
            } catch (BankingException refusal) {
                // A random debit larger than a customer's funds is the only acceptable refusal.
                assertThat(refusal.getErrorCode()).isEqualTo(LedgerErrorCode.INSUFFICIENT_FUNDS);
                refused++;
            }
        }
        assertThat(posted).isGreaterThan(150);
        assertThat(posted + refused).isEqualTo(250);

        ReconciliationReport report = inTenant(tenant.id(), () -> ledgerQueries.reconcile());
        assertThat(report.balanceBreaks()).isEmpty();
        assertThat(report.unbalancedJournals()).isEmpty();
        assertThat(report.intact()).isTrue();

        for (String currency : CURRENCIES) {
            TrialBalanceResponse whole = inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, currency, null));
            assertThat(whole.balanced()).as("%s trial balance", currency).isTrue();
            assertThat(row(whole, "1910").debitBalance().subtract(row(whole, "1910").creditBalance()))
                    .as("%s: what branches are owed equals what branches owe", currency)
                    .isEqualByComparingTo(row(whole, "2910").creditBalance().subtract(row(whole, "2910").debitBalance()));
            for (UUID branch : branches) {
                TrialBalanceResponse perBranch = inTenant(tenant.id(),
                        () -> ledgerQueries.trialBalance(null, currency, branch));
                assertThat(perBranch.balanced()).as("%s trial balance of branch %s", currency, branch).isTrue();
            }
        }
        for (String currency : CURRENCIES) {
            for (UUID account : accountsByCurrency.get(currency)) {
                assertThat(balanceOf(tenant.id(), account)).as("balance-checked accounts never go negative")
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO);
            }
        }
    }

    @Test
    void everyUnbalancedJournalIsRejected() {
        Random random = new Random(42L);
        long journalsBefore = inTenant(tenant.id(), () -> ledgerQueries.reconcile()).journalsChecked();
        for (int iteration = 0; iteration < 100; iteration++) {
            String currency = CURRENCIES.get(random.nextInt(CURRENCIES.size()));
            List<PostingLine> lines = new ArrayList<>(randomBalancedLines(random, currency));
            PostingLine victim = lines.get(random.nextInt(lines.size()));
            BigDecimal skew = BigDecimal.valueOf(random.nextInt(1, 500), 2);
            lines.set(lines.indexOf(victim), new PostingLine(victim.ledgerAccountId(), victim.chartOfAccountId(),
                    victim.systemAccount(), victim.branchId(), victim.currency(), victim.direction(),
                    victim.amount().add(skew), victim.narration()));
            assertFailsWith(LedgerErrorCode.UNBALANCED_JOURNAL, () -> post(tenant.id(), branches.get(0), lines));
        }
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).journalsChecked()).isEqualTo(journalsBefore);
    }

    /**
     * Two to six lines in one currency across random branches, mixing customer accounts and GL accounts; the last
     * line balances the rest.
     */
    private List<PostingLine> randomBalancedLines(Random random, String currency) {
        List<PostingLine> lines = new ArrayList<>();
        BigDecimal net = BigDecimal.ZERO;
        int count = random.nextInt(1, 6);
        for (int index = 0; index < count; index++) {
            EntryDirection direction = random.nextBoolean() ? EntryDirection.DEBIT : EntryDirection.CREDIT;
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 50_000), 2);
            lines.add(randomTarget(random, currency, direction, amount));
            net = direction == EntryDirection.DEBIT ? net.add(amount) : net.subtract(amount);
        }
        if (net.signum() == 0) {
            BigDecimal amount = BigDecimal.valueOf(random.nextInt(1, 50_000), 2);
            lines.add(glLine(random, currency, EntryDirection.DEBIT, amount));
            net = amount;
        }
        lines.add(glLine(random, currency, net.signum() > 0 ? EntryDirection.CREDIT : EntryDirection.DEBIT, net.abs()));
        return lines;
    }

    private PostingLine randomTarget(Random random, String currency, EntryDirection direction, BigDecimal amount) {
        if (random.nextBoolean()) {
            List<UUID> accounts = accountsByCurrency.get(currency);
            return PostingLine.toLedgerAccount(accounts.get(random.nextInt(accounts.size())), direction, amount, null);
        }
        return glLine(random, currency, direction, amount);
    }

    private PostingLine glLine(Random random, String currency, EntryDirection direction, BigDecimal amount) {
        return PostingLine.toSystemAccount(GL_TARGETS.get(random.nextInt(GL_TARGETS.size())),
                branches.get(random.nextInt(branches.size())), currency, direction, amount, null);
    }

    private static TrialBalanceRow row(TrialBalanceResponse trialBalance, String code) {
        return trialBalance.rows().stream().filter(row -> row.code().equals(code)).findFirst().orElseThrow();
    }
}
