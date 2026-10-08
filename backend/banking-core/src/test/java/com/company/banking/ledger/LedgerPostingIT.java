package com.company.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.ledger.dto.ChartOfAccountResponse;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.dto.ReversalRequest;
import com.company.banking.ledger.dto.TrialBalanceResponse;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

class LedgerPostingIT extends LedgerIntegrationTest {

    private TenantHandle tenant;
    private UUID headOffice;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        headOffice = tenant.headOfficeId();
    }

    @Test
    void onboardingProvisionsTheDefaultChartAndOpensTheCurrentPeriod() {
        List<ChartOfAccountResponse> chart = inTenant(tenant.id(), () -> chartOfAccounts.list());

        assertThat(chart).extracting(ChartOfAccountResponse::systemCode)
                .contains("CASH_AT_BRANCH", "SAVINGS_DEPOSITS", "INTER_BRANCH_DUE_FROM", "INTER_BRANCH_DUE_TO",
                        "TRANSACTION_FEE_INCOME", "RETAINED_EARNINGS");
        ChartOfAccountResponse provision = chart.stream().filter(gl -> "LOAN_LOSS_PROVISION".equals(gl.systemCode()))
                .findFirst().orElseThrow();
        assertThat(provision.accountClass()).isEqualTo("ASSET");
        assertThat(provision.normalSide()).isEqualTo("CREDIT");
        assertThat(chart.stream().filter(ChartOfAccountResponse::header).map(ChartOfAccountResponse::code))
                .contains("1000", "2000", "3000", "4000", "5000");

        JsonNode periodsPage = api.get("/api/v1/ledger/periods", accountant().token()).expect(200).data();
        assertThat(periodsPage.get("items").get(0).get("status").asString()).isEqualTo("OPEN");
    }

    @Test
    void aDepositCreditsTheCustomerAndBalancesTheBooks() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Ama Mensah savings");

        PostedJournal journal = deposit(tenant.id(), account, headOffice, "GHS", "1250.50");

        assertThat(journal.lines()).hasSize(2);
        assertThat(journal.balanceOf(account).ledgerBalance()).isEqualByComparingTo("1250.50");
        assertThat(journal.balanceOf(account).availableBalance()).isEqualTo(new BigDecimal("1250.50"));
        assertThat(journal.journalNumber()).matches("JE-\\d{8}-[0-9A-Z]{12}");

        TrialBalanceResponse trialBalance = inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, null, null));
        assertThat(trialBalance.balanced()).isTrue();
        assertThat(row(trialBalance, "1110").debitBalance()).isEqualByComparingTo("1250.50");
        assertThat(row(trialBalance, "2110").creditBalance()).isEqualByComparingTo("1250.50");
        assertThat(row(trialBalance, "2000").creditBalance()).isEqualByComparingTo("1250.50");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void theApiShowsJournalsWithMoneyAsDecimalStrings() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Kofi savings");
        PostedJournal journal = deposit(tenant.id(), account, headOffice, "GHS", "99.9");

        JsonNode detail = api.get("/api/v1/ledger/journals/" + journal.id(), accountant().token()).expect(200).data();

        JsonNode line = detail.get("lines").get(0);
        assertThat(line.get("amount").isString()).isTrue();
        assertThat(line.get("amount").asString()).isEqualTo("99.90");
        assertThat(line.get("direction").asString()).isEqualTo("DEBIT");
        assertThat(line.get("glCode").asString()).isEqualTo("1110");
        JsonNode search = api.get("/api/v1/ledger/journals?sourceType=TRANSACTION", accountant().token())
                .expect(200).data();
        assertThat(search.get("totalItems").asLong()).isEqualTo(1);
        JsonNode trial = api.get("/api/v1/ledger/trial-balance", accountant().token()).expect(200).data();
        assertThat(trial.get("totalDebitBalance").asString()).isEqualTo("99.90");
        assertThat(trial.get("balanced").asBoolean()).isTrue();
    }

    @Test
    void ledgerViewsRequireLedgerPermissions() {
        StaffHandle teller = fixtures.createStaff(tenant, "teller", headOffice, false, "TELLER");
        api.get("/api/v1/ledger/journals", teller.token()).expectError(403, "ACCESS_DENIED");
        api.get("/api/v1/ledger/trial-balance", teller.token()).expectError(403, "ACCESS_DENIED");
        api.post("/api/v1/ledger/periods/2026-01-01/close", tenant.adminToken(), Map.of())
                .expectError(403, "ACCESS_DENIED");
    }

    @Test
    void unbalancedAndInvalidPostingsAreRejectedBeforeAnythingIsWritten() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");

        assertFailsWith(LedgerErrorCode.UNBALANCED_JOURNAL, () -> post(tenant.id(), headOffice, List.of(
                cash(EntryDirection.DEBIT, "100.00"),
                PostingLine.toLedgerAccount(account, EntryDirection.CREDIT, money("99.99"), null))));
        assertFailsWith(LedgerErrorCode.INVALID_AMOUNT, () -> deposit(tenant.id(), account, headOffice, "GHS", "10.001"));
        assertFailsWith(LedgerErrorCode.INVALID_AMOUNT, () -> deposit(tenant.id(), account, headOffice, "GHS", "0"));
        assertFailsWith(LedgerErrorCode.INVALID_AMOUNT, () -> deposit(tenant.id(), account, headOffice, "GHS", "-5"));
        assertFailsWith(LedgerErrorCode.INVALID_AMOUNT,
                () -> deposit(tenant.id(), account, headOffice, "GHS", "1000000000000000"));
        assertFailsWith(LedgerErrorCode.CURRENCY_NOT_SUPPORTED, () -> post(tenant.id(), headOffice, List.of(
                PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, headOffice, "XYZ", EntryDirection.DEBIT,
                        money("1"), null),
                PostingLine.toSystemAccount(SystemAccount.SUSPENSE_CREDIT, headOffice, "XYZ", EntryDirection.CREDIT,
                        money("1"), null))));
        UUID header = gl("2100").id();
        assertFailsWith(LedgerErrorCode.GL_ACCOUNT_NOT_POSTABLE, () -> post(tenant.id(), headOffice, List.of(
                cash(EntryDirection.DEBIT, "5.00"),
                PostingLine.toGl(header, headOffice, "GHS", EntryDirection.CREDIT, money("5.00"), null))));
        assertFailsWith(LedgerErrorCode.INVALID_POSTING, () -> post(tenant.id(), headOffice, List.of(
                cash(EntryDirection.DEBIT, "5.00"))));

        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).journalsChecked()).isZero();
        assertThat(balanceOf(tenant.id(), account)).isZero();
    }

    @Test
    void withdrawalsCannotOverdrawUnlessAnOverdraftIsAllowed() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        deposit(tenant.id(), account, headOffice, "GHS", "100.00");

        assertFailsWith(LedgerErrorCode.INSUFFICIENT_FUNDS,
                () -> withdraw(tenant.id(), account, headOffice, "GHS", "100.01"));
        withdraw(tenant.id(), account, headOffice, "GHS", "100.00");
        assertThat(balanceOf(tenant.id(), account)).isZero();

        inTenant(tenant.id(), () -> {
            ledgerAccounts.setOverdraftLimit(account, money("50.00"));
            return null;
        });
        PostedJournal overdrawn = withdraw(tenant.id(), account, headOffice, "GHS", "50.00");
        assertThat(overdrawn.balanceOf(account).ledgerBalance()).isEqualByComparingTo("-50.00");
        assertFailsWith(LedgerErrorCode.INSUFFICIENT_FUNDS,
                () -> withdraw(tenant.id(), account, headOffice, "GHS", "0.01"));
    }

    @Test
    void aJournalPassingMoneyThroughAThinlyFundedAccountIsNotRefusedHalfway() {
        // Found by LedgerPropertyIT: the database checks the overdraft rule line by line, so a journal that debits
        // before it credits the same account must not fail on its intermediate balance.
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        deposit(tenant.id(), account, headOffice, "GHS", "100.00");

        PostedJournal journal = post(tenant.id(), headOffice, List.of(
                PostingLine.toLedgerAccount(account, EntryDirection.DEBIT, money("300.00"), "Out"),
                PostingLine.toLedgerAccount(account, EntryDirection.CREDIT, money("300.00"), "Back in")));

        assertThat(journal.balanceOf(account).ledgerBalance()).isEqualByComparingTo("100.00");
        assertThat(journal.lines().get(0).direction()).isEqualTo(EntryDirection.CREDIT);
    }

    @Test
    void aDepositAtAnotherBranchBalancesEveryBranchThroughInterBranchAccounts() {
        UUID kumasi = fixtures.createBranch(tenant, "KSI");
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Head office customer");

        PostedJournal journal = deposit(tenant.id(), account, kumasi, "GHS", "300.00");

        assertThat(journal.lines()).hasSize(4);
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.glCode()).isEqualTo("2910");
            assertThat(line.branchId()).isEqualTo(kumasi);
            assertThat(line.direction()).isEqualTo(EntryDirection.CREDIT);
        });
        assertThat(journal.lines()).anySatisfy(line -> {
            assertThat(line.glCode()).isEqualTo("1910");
            assertThat(line.branchId()).isEqualTo(headOffice);
            assertThat(line.direction()).isEqualTo(EntryDirection.DEBIT);
        });
        for (UUID branch : List.of(headOffice, kumasi)) {
            TrialBalanceResponse perBranch = inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", branch));
            assertThat(perBranch.balanced()).as("branch %s balances on its own", branch).isTrue();
        }
        TrialBalanceResponse whole = inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null));
        assertThat(row(whole, "1910").debitBalance()).isEqualByComparingTo(row(whole, "2910").creditBalance());
    }

    @Test
    void reversalsMirrorTheJournalExactlyOnce() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        PostedJournal original = deposit(tenant.id(), account, headOffice, "GHS", "80.00");

        PostedJournal reversal = inTenant(tenant.id(),
                () -> postingEngine.reverse(new ReversalRequest(original.id(), "Posted to the wrong account", null, null)));

        assertThat(reversal.lines()).hasSize(original.lines().size());
        for (int index = 0; index < original.lines().size(); index++) {
            assertThat(reversal.lines().get(index).direction())
                    .isEqualTo(original.lines().get(index).direction().opposite());
            assertThat(reversal.lines().get(index).amount()).isEqualTo(original.lines().get(index).amount());
        }
        assertThat(balanceOf(tenant.id(), account)).isZero();
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.journal(original.id())).reversedByJournalId())
                .isEqualTo(reversal.id());
        assertFailsWith(LedgerErrorCode.JOURNAL_ALREADY_REVERSED, () -> inTenant(tenant.id(),
                () -> postingEngine.reverse(new ReversalRequest(original.id(), "Again", null, null))));
        assertFailsWith(LedgerErrorCode.REVERSAL_NOT_ALLOWED, () -> inTenant(tenant.id(),
                () -> postingEngine.reverse(new ReversalRequest(reversal.id(), "Undo the undo", null, null))));
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void aReversalCannotOverdrawAnAccountWhoseMoneyIsAlreadySpent() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        PostedJournal deposit = deposit(tenant.id(), account, headOffice, "GHS", "50.00");
        withdraw(tenant.id(), account, headOffice, "GHS", "30.00");

        assertFailsWith(LedgerErrorCode.INSUFFICIENT_FUNDS, () -> inTenant(tenant.id(),
                () -> postingEngine.reverse(new ReversalRequest(deposit.id(), "Bounced", null, null))));
        assertThat(balanceOf(tenant.id(), account)).isEqualByComparingTo("20.00");
    }

    @Test
    void manualJournalsNeedASecondPersonAndManualGlAccounts() {
        UUID approver = UUID.randomUUID();
        List<PostingLine> lines = List.of(
                PostingLine.toSystemAccount(SystemAccount.OPERATING_EXPENSE, headOffice, "GHS", EntryDirection.DEBIT,
                        money("40.00"), "Stationery"),
                PostingLine.toSystemAccount(SystemAccount.SUSPENSE_CREDIT, headOffice, "GHS", EntryDirection.CREDIT,
                        money("40.00"), "Stationery"));

        assertFailsWith(LedgerErrorCode.APPROVAL_REQUIRED, () -> manual(lines, null));
        assertFailsWith(LedgerErrorCode.GL_ACCOUNT_NOT_MANUAL, () -> manual(List.of(
                cash(EntryDirection.DEBIT, "10.00"),
                PostingLine.toSystemAccount(SystemAccount.SUSPENSE_CREDIT, headOffice, "GHS", EntryDirection.CREDIT,
                        money("10.00"), null)), approver));
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        assertFailsWith(LedgerErrorCode.GL_ACCOUNT_NOT_MANUAL, () -> manual(List.of(
                PostingLine.toSystemAccount(SystemAccount.OPERATING_EXPENSE, headOffice, "GHS", EntryDirection.DEBIT,
                        money("10.00"), null),
                PostingLine.toLedgerAccount(account, EntryDirection.CREDIT, money("10.00"), null)), approver));

        PostedJournal posted = manual(lines, approver);
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.journal(posted.id())).approvedBy()).isEqualTo(approver);
    }

    @Test
    void closedPeriodsAcceptNoPostingsAndNeverReopen() {
        LocalDate lastMonth = LocalDate.now().minusMonths(1).withDayOfMonth(1);
        inTenant(tenant.id(), () -> {
            periods.requireOpen(lastMonth);
            return null;
        });
        assertFailsWith(LedgerErrorCode.PERIOD_CLOSE_NOT_ALLOWED,
                () -> inTenant(tenant.id(), () -> periods.close(LocalDate.now().withDayOfMonth(1))));

        StaffHandle accountant = accountant();
        api.post("/api/v1/ledger/periods/" + lastMonth + "/close", accountant.token(), Map.of()).expect(200);

        assertFailsWith(LedgerErrorCode.PERIOD_CLOSED, () -> inTenant(tenant.id(), () -> {
            periods.requireOpen(lastMonth.plusDays(3));
            return null;
        }));
        assertFailsWith(LedgerErrorCode.PERIOD_CLOSED, () -> inTenant(tenant.id(), () -> {
            periods.requireOpen(lastMonth.minusMonths(2));
            return null;
        }));
        api.post("/api/v1/ledger/periods/" + lastMonth + "/close", accountant.token(), Map.of())
                .expectError(422, "PERIOD_CLOSE_NOT_ALLOWED");
    }

    @Test
    void theChartCanBeExtendedButNotBroken() {
        String admin = tenant.adminToken();
        UUID depositsHeader = gl("2100").id();

        JsonNode created = api.post("/api/v1/ledger/chart-of-accounts", admin, Map.of(
                "code", "2160", "name", "Group deposits", "accountClass", "LIABILITY",
                "parentId", depositsHeader.toString())).expect(201).data();
        assertThat(created.get("normalSide").asString()).isEqualTo("CREDIT");

        api.post("/api/v1/ledger/chart-of-accounts", admin, Map.of(
                        "code", "2160", "name", "Duplicate", "accountClass", "LIABILITY",
                        "parentId", depositsHeader.toString()))
                .expectError(409, "DUPLICATE_RESOURCE");
        api.post("/api/v1/ledger/chart-of-accounts", admin, Map.of(
                        "code", "2170", "name", "Wrong class", "accountClass", "ASSET",
                        "parentId", depositsHeader.toString()))
                .expectError(422, "INVALID_PARENT_ACCOUNT");

        GlAccountRef cash = gl("1110");
        api.put("/api/v1/ledger/chart-of-accounts/" + cash.id(), admin, Map.of(
                        "name", "Cash", "manualPostingAllowed", false, "status", "INACTIVE", "version", 0))
                .expectError(422, "SYSTEM_GL_ACCOUNT_PROTECTED");

        UUID custom = UUID.fromString(created.get("id").asString());
        api.put("/api/v1/ledger/chart-of-accounts/" + custom, admin, Map.of(
                "name", "Group savings deposits", "manualPostingAllowed", false, "status", "INACTIVE",
                "version", created.get("version").asLong())).expect(200);
    }

    @Test
    void anotherTenantsLedgerIsInvisible() {
        UUID account = openDepositAccount(tenant.id(), headOffice, "GHS", "Account");
        PostedJournal journal = deposit(tenant.id(), account, headOffice, "GHS", "10.00");
        TenantHandle other = fixtures.onboardTenant();
        StaffHandle otherAccountant = fixtures.createStaff(other, "accountant", other.headOfficeId(), true,
                "ACCOUNTANT");

        api.get("/api/v1/ledger/journals/" + journal.id(), otherAccountant.token())
                .expectError(404, CommonErrorCode.RESOURCE_NOT_FOUND.code());
        assertFailsWith(LedgerErrorCode.INVALID_POSTING,
                () -> deposit(other.id(), account, other.headOfficeId(), "GHS", "1.00"));
    }

    private PostedJournal manual(List<PostingLine> lines, UUID approvedBy) {
        return inTenant(tenant.id(), () -> postingEngine.post(new PostingRequest(JournalSource.MANUAL, "MJ-1", null,
                headOffice, null, "Manual adjustment", approvedBy, lines)));
    }

    private PostingLine cash(EntryDirection direction, String amount) {
        return PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, headOffice, "GHS", direction, money(amount),
                null);
    }

    private GlAccountRef gl(String code) {
        return inTenant(tenant.id(), () -> chartOfAccounts.list().stream()
                .filter(account -> account.code().equals(code))
                .findFirst()
                .map(account -> chartOfAccounts.get(account.id()))
                .orElseThrow());
    }

    private StaffHandle accountant() {
        return fixtures.createStaff(tenant, "acct" + UUID.randomUUID().toString().substring(0, 6), headOffice, true,
                "ACCOUNTANT");
    }

    private static TrialBalanceRow row(TrialBalanceResponse trialBalance, String code) {
        return trialBalance.rows().stream().filter(row -> row.code().equals(code)).findFirst().orElseThrow();
    }
}
