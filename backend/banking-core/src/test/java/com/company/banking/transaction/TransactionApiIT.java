package com.company.banking.transaction;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.ledger.dto.TrialBalanceResponse;
import com.company.banking.ledger.dto.TrialBalanceRow;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Api;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.transaction.dto.CashWithdrawalRequest;
import com.company.banking.transaction.dto.TransferRequest;
import com.company.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.JsonNode;

/**
 * Deposits, withdrawals and transfers through the API, including the spec §64 checks: balances move as expected,
 * every journal balances, a retried request posts once, concurrent withdrawals never overdraw, and another
 * institution's accounts are unreachable.
 */
class TransactionApiIT extends LedgerIntegrationTest {

    private static final String DEPOSITS = "/api/v1/transactions/deposits";
    private static final String WITHDRAWALS = "/api/v1/transactions/withdrawals";
    private static final String TRANSFERS = "/api/v1/transactions/transfers";

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcClient jdbcClient;

    private TenantHandle tenant;
    private StaffHandle manager;
    private StaffHandle teller;
    private UUID customer;
    private String current;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), false, "LOAN_OFFICER");
        manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), false, "BRANCH_MANAGER");
        teller = fixtures.createStaff(tenant, "teller", tenant.headOfficeId(), false, "TELLER");
        customer = fixtures.verifiedIndividual(officer, manager, tenant.headOfficeId(), "Ama");
        current = fixtures.publishedProduct(tenant, "CURR01", "CURRENT", terms("0"));
    }

    @Test
    void depositsAndWithdrawalsMoveTheBalanceAndEveryJournalBalances() {
        JsonNode account = fixtures.openAccount(manager.token(), customer, current);
        String accountId = account.get("id").asString();

        JsonNode deposited = api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId,
                "amount", "500.00", "narration", "Market takings", "externalReference", "SLIP-001")).expect(201).data();
        assertThat(deposited.at("/transaction/transactionType").asString()).isEqualTo("CASH_DEPOSIT");
        assertThat(deposited.at("/transaction/status").asString()).isEqualTo("POSTED");
        assertThat(deposited.at("/transaction/amount").asString()).isEqualTo("500.00");
        assertThat(deposited.at("/transaction/feeAmount").asString()).isEqualTo("0.00");
        assertThat(deposited.at("/transaction/reference").asString()).matches("^TXN-[0-9]{8}-[0-9A-Z]{12}$");
        assertThat(deposited.at("/transaction/creditAccountNumber").asString())
                .isEqualTo(account.get("accountNumber").asString());
        assertThat(deposited.at("/transaction/initiatedBy").asString()).isEqualTo(teller.id().toString());
        assertThat(deposited.at("/balances/0/ledgerBalance").asString()).isEqualTo("500.00");

        JsonNode withdrawn = api.postIdempotent(WITHDRAWALS, teller.token(), key(), Map.of("accountId", accountId,
                "amount", "120.50")).expect(201).data();
        assertThat(withdrawn.at("/transaction/debitAccountId").asString()).isEqualTo(accountId);
        assertThat(withdrawn.at("/balances/0/availableBalance").asString()).isEqualTo("379.50");
        assertThat(api.get("/api/v1/accounts/" + accountId, teller.token()).expect(200).data()
                .get("ledgerBalance").asString()).isEqualTo("379.50");

        UUID journalId = UUID.fromString(withdrawn.at("/transaction/journalEntryId").asString());
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.journal(journalId)).lines()).hasSize(2);
        assertBooksIntact();

        JsonNode history = api.get("/api/v1/accounts/" + accountId + "/transactions", teller.token())
                .expect(200).data();
        assertThat(history.get("items")).hasSize(2);
        assertThat(history.at("/items/0/transactionType").asString()).as("newest first").isEqualTo("CASH_WITHDRAWAL");
        String transactionId = deposited.at("/transaction/id").asString();
        assertThat(api.get("/api/v1/transactions/" + transactionId, manager.token()).expect(200).data()
                .get("externalReference").asString()).isEqualTo("SLIP-001");
        assertThat(auditCount("TRANSACTION_POSTED", transactionId)).isEqualTo(1);
        assertThat(outboxCount("TRANSACTION_POSTED")).isEqualTo(2);
    }

    @Test
    void aRetriedRequestPostsOnceAndAReusedKeyIsRefused() {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        String key = key();
        Map<String, Object> deposit = Map.of("accountId", accountId, "amount", "75.00");

        Api.Response first = api.postIdempotent(DEPOSITS, teller.token(), key, deposit).expect(201);
        Api.Response second = api.postIdempotent(DEPOSITS, teller.token(), key, deposit).expect(201);
        Api.Response third = api.postIdempotent(DEPOSITS, teller.token(), key, deposit).expect(201);
        assertThat(first.header("Idempotency-Replayed")).isEqualTo("false");
        assertThat(second.header("Idempotency-Replayed")).isEqualTo("true");
        assertThat(third.data().at("/transaction/id").asString())
                .isEqualTo(first.data().at("/transaction/id").asString());
        assertThat(balance(accountId)).isEqualTo("75.00");

        api.postIdempotent(DEPOSITS, teller.token(), key, Map.of("accountId", accountId, "amount", "76.00"))
                .expectError(422, "IDEMPOTENCY_KEY_REUSED");
        api.postIdempotent(DEPOSITS, teller.token(), null, deposit).expectError(400, "IDEMPOTENCY_KEY_REQUIRED");
        api.postIdempotent(DEPOSITS, teller.token(), "short", deposit).expectError(400, "INVALID_IDEMPOTENCY_KEY");
        assertThat(balance(accountId)).isEqualTo("75.00");
        assertThat(api.get("/api/v1/accounts/" + accountId + "/transactions", teller.token()).expect(200).data()
                .get("items")).hasSize(1);
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "100.00"))
                .expect(201);

        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Callable<Boolean> withdrawal = () -> {
                start.await();
                try {
                    inTenant(tenant.id(), () -> transactionService.withdraw(key(), new CashWithdrawalRequest(
                            UUID.fromString(accountId), null, money("10.00"), null, null)));
                    return true;
                } catch (BankingException refused) {
                    assertThat(refused.getErrorCode().code()).isEqualTo("INSUFFICIENT_FUNDS");
                    return false;
                }
            };
            results.add(pool.submit(withdrawal));
        }
        start.countDown();
        int succeeded = 0;
        for (Future<Boolean> result : results) {
            succeeded += result.get() ? 1 : 0;
        }
        pool.shutdown();

        assertThat(succeeded).isEqualTo(10);
        assertThat(balance(accountId)).isEqualTo("0.00");
        assertBooksIntact();
    }

    @Test
    void transfersInBothDirectionsAtOnceDoNotDeadlockOrLoseMoney() throws Exception {
        UUID first = UUID.fromString(fixtures.openAccount(manager.token(), customer, current).get("id").asString());
        UUID second = UUID.fromString(fixtures.openAccount(manager.token(), customer, current).get("id").asString());
        for (UUID account : List.of(first, second)) {
            api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", account.toString(),
                    "amount", "1000.00")).expect(201);
        }

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            UUID from = i % 2 == 0 ? first : second;
            UUID to = i % 2 == 0 ? second : first;
            results.add(pool.submit(() -> {
                start.await();
                return inTenant(tenant.id(), () -> transactionService.transfer(key(),
                        new TransferRequest(from, to, money("7.25"), null, null)));
            }));
        }
        start.countDown();
        for (Future<?> result : results) {
            result.get();
        }
        pool.shutdown();

        assertThat(balance(first.toString())).isEqualTo("1000.00");
        assertThat(balance(second.toString())).isEqualTo("1000.00");
        assertBooksIntact();
    }

    @Test
    void productLimitsAndChargesApply() {
        Map<String, Object> limited = terms("0");
        limited.put("minOperatingBalance", "20.00");
        limited.put("maxWithdrawalAmount", "300.00");
        limited.put("dailyWithdrawalLimit", "500.00");
        limited.put("charges", List.of(
                Map.of("event", "CASH_WITHDRAWAL", "name", "Withdrawal fee", "calculation", "PERCENT",
                        "rate", "1", "minAmount", "1.00", "maxAmount", "5.00"),
                Map.of("event", "TRANSFER_OUT", "name", "Transfer fee", "calculation", "FLAT", "flatAmount", "2.00")));
        String productId = fixtures.publishedProduct(tenant, "SAVE01", "SAVINGS", limited);
        JsonNode product = api.get("/api/v1/products/" + productId, manager.token()).expect(200).data();
        assertThat(product.at("/currentVersion/charges")).hasSize(2);
        String accountId = fixtures.openAccount(manager.token(), customer, productId).get("id").asString();
        String otherId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        BigDecimal feesBefore = feeIncome();
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "1000.00"))
                .expect(201);

        withdraw(accountId, "300.01").expectError(422, "WITHDRAWAL_LIMIT_EXCEEDED");
        JsonNode withdrawn = withdraw(accountId, "300.00").expect(201).data();
        assertThat(withdrawn.at("/transaction/feeAmount").asString()).isEqualTo("3.00");
        assertThat(withdrawn.at("/balances/0/ledgerBalance").asString()).isEqualTo("697.00");
        withdraw(accountId, "200.01").expectError(422, "DAILY_LIMIT_EXCEEDED");
        assertThat(withdraw(accountId, "50.00").expect(201).data().at("/transaction/feeAmount").asString())
                .as("minimum charge").isEqualTo("1.00");

        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", accountId,
                "toAccountId", otherId, "amount", "150.01")).expectError(422, "DAILY_LIMIT_EXCEEDED");
        JsonNode transferred = api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId",
                accountId, "toAccountId", otherId, "amount", "100.00")).expect(201).data();
        assertThat(transferred.at("/transaction/feeAmount").asString()).isEqualTo("2.00");
        assertThat(balance(accountId)).isEqualTo("544.00");
        assertThat(balance(otherId)).as("the receiver pays nothing").isEqualTo("100.00");
        assertThat(feeIncome().subtract(feesBefore)).isEqualByComparingTo("6.00");
        assertBooksIntact();
    }

    @Test
    void minimumAndMaximumBalancesAreKept() {
        Map<String, Object> bounded = terms("0");
        bounded.put("minOperatingBalance", "20.00");
        bounded.put("maxBalance", "1000.00");
        String productId = fixtures.publishedProduct(tenant, "SAVE01", "SAVINGS", bounded);
        String accountId = fixtures.openAccount(manager.token(), customer, productId).get("id").asString();

        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "1000.01"))
                .expectError(422, "MAXIMUM_BALANCE_EXCEEDED");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "1000.00"))
                .expect(201);
        withdraw(accountId, "980.01").expectError(422, "MINIMUM_BALANCE_REQUIRED");
        withdraw(accountId, "980.00").expect(201);
        assertThat(balance(accountId)).isEqualTo("20.00");
    }

    @Test
    void theAccountStatusDecidesWhichWayMoneyMayMove() {
        String savings = fixtures.publishedProduct(tenant, "SAVE01", "SAVINGS", terms("50.00"));
        JsonNode pending = fixtures.openAccount(manager.token(), customer, savings);
        String pendingId = pending.get("id").asString();
        withdraw(pendingId, "1.00").expectError(422, "ACCOUNT_NOT_DEBITABLE");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", pendingId, "amount", "30.00"))
                .expect(201);
        assertThat(status(pendingId)).as("below the opening balance").isEqualTo("PENDING");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", pendingId, "amount", "20.00"))
                .expect(201);
        assertThat(status(pendingId)).isEqualTo("ACTIVE");
        assertThat(auditCount("ACCOUNT_ACTIVATED", pendingId)).isEqualTo(1);

        JsonNode restricted = changeStatus(pendingId, "RESTRICTED");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", pendingId, "amount", "5.00"))
                .expect(201);
        withdraw(pendingId, "1.00").expectError(422, "ACCOUNT_NOT_DEBITABLE");
        changeStatus(pendingId, "FROZEN", restricted.get("version").asLong());
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", pendingId, "amount", "5.00"))
                .expectError(422, "ACCOUNT_NOT_CREDITABLE");

        String activeId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", activeId, "amount", "100.00"))
                .expect(201);
        api.post("/api/v1/accounts/" + activeId + "/holds", manager.token(), Map.of("amount", "80.00",
                "holdType", "LIEN", "reason", "Collateral")).expect(201);
        withdraw(activeId, "20.01").expectError(422, "INSUFFICIENT_FUNDS");
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", activeId,
                "toAccountId", pendingId, "amount", "1.00")).expectError(422, "ACCOUNT_NOT_CREDITABLE");
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", activeId,
                "toAccountId", activeId, "amount", "1.00")).expectError(422, "SAME_ACCOUNT_TRANSFER");
    }

    @Test
    void amountsAreValidatedAndOnlyCashiersMoveMoney() {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        String dollars = fixtures.publishedProduct(tenant, "USD01", "CURRENT", usd());
        String dollarAccount = fixtures.openAccount(manager.token(), customer, dollars).get("id").asString();

        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "10.001"))
                .expectError(422, "INVALID_AMOUNT");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "-5.00"))
                .expect(400);
        api.postIdempotent(DEPOSITS, manager.token(), key(), Map.of("accountId", accountId, "amount", "5.00"))
                .expectError(403, "ACCESS_DENIED");
        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "amount", "50.00"))
                .expect(201);
        api.postIdempotent(TRANSFERS, teller.token(), key(), Map.of("fromAccountId", accountId,
                "toAccountId", dollarAccount, "amount", "1.00")).expectError(422, "CURRENCY_MISMATCH");
    }

    @Test
    void anotherInstitutionCannotReachTheAccountsOrTransactions() {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        String transactionId = api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId,
                "amount", "40.00")).expect(201).data().at("/transaction/id").asString();

        TenantHandle other = fixtures.onboardTenant();
        StaffHandle intruder = fixtures.createStaff(other, "teller", other.headOfficeId(), true, "TELLER");
        api.postIdempotent(WITHDRAWALS, intruder.token(), key(), Map.of("accountId", accountId, "amount", "1.00"))
                .expectError(404, "RESOURCE_NOT_FOUND");
        api.postIdempotent(DEPOSITS, intruder.token(), key(), Map.of("accountId", accountId, "amount", "1.00"))
                .expectError(404, "RESOURCE_NOT_FOUND");
        api.get("/api/v1/transactions/" + transactionId, intruder.token()).expectError(404, "RESOURCE_NOT_FOUND");
        api.get("/api/v1/accounts/" + accountId + "/transactions", intruder.token())
                .expectError(404, "RESOURCE_NOT_FOUND");
        assertThat(balance(accountId)).isEqualTo("40.00");
    }

    @Test
    void cashMovesThroughBranchesInScopeAndBranchesBalanceEachOther() {
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle cashier = fixtures.createStaff(tenant, "cashier", kumasi, true, "TELLER");
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();

        api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of("accountId", accountId, "branchId",
                kumasi.toString(), "amount", "10.00")).expectError(404, "RESOURCE_NOT_FOUND");
        JsonNode deposited = api.postIdempotent(DEPOSITS, cashier.token(), key(), Map.of("accountId", accountId,
                "branchId", kumasi.toString(), "amount", "60.00")).expect(201).data();
        assertThat(deposited.at("/transaction/branchId").asString()).isEqualTo(kumasi.toString());

        UUID journalId = UUID.fromString(deposited.at("/transaction/journalEntryId").asString());
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.journal(journalId)).lines())
                .as("cash, deposit and the two inter-branch lines").hasSize(4);
        TrialBalanceResponse kumasiBooks = inTenant(tenant.id(),
                () -> ledgerQueries.trialBalance(null, "GHS", kumasi));
        assertThat(kumasiBooks.balanced()).isTrue();
        assertBooksIntact();
    }

    @Test
    void theDatabaseNeverLetsATransactionRecordChange() {
        String accountId = fixtures.openAccount(manager.token(), customer, current).get("id").asString();
        UUID transactionId = UUID.fromString(api.postIdempotent(DEPOSITS, teller.token(), key(), Map.of(
                "accountId", accountId, "amount", "40.00")).expect(201).data().at("/transaction/id").asString());

        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "UPDATE core.financial_transaction SET amount = 1 WHERE id = :id")
                .param("id", transactionId).update()))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> jdbcClient.sql(
                        "DELETE FROM core.financial_transaction WHERE id = :id")
                .param("id", transactionId).update()))
                .isInstanceOf(DataAccessException.class);
        // A journal claiming a transaction that does not exist is refused when the transaction commits.
        UUID ledgerAccount = inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT ledger_account_id FROM core.account WHERE id = :id")
                .param("id", UUID.fromString(accountId)).query(UUID.class).single());
        PostingRequest orphan = new PostingRequest(JournalSource.TRANSACTION, "ORPHAN", UUID.randomUUID(),
                tenant.headOfficeId(), null, "Orphan journal", null, List.of(
                PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, tenant.headOfficeId(), "GHS",
                        EntryDirection.DEBIT, money("1.00"), "Orphan"),
                PostingLine.toLedgerAccount(ledgerAccount, EntryDirection.CREDIT, money("1.00"), "Orphan")));
        assertThatThrownBy(() -> inTenant(tenant.id(), () -> postingEngine.post(orphan)))
                .hasStackTraceContaining("fk_journal_entry_financial_transaction");
        assertThat(balance(accountId)).isEqualTo("40.00");
    }

    // ---------------------------------------------------------------------------------------------------------

    private Api.Response withdraw(String accountId, String amount) {
        return api.postIdempotent(WITHDRAWALS, teller.token(), key(), Map.of("accountId", accountId,
                "amount", amount));
    }

    private String balance(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("ledgerBalance")
                .asString();
    }

    private String status(String accountId) {
        return api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("status")
                .asString();
    }

    private JsonNode changeStatus(String accountId, String status) {
        long version = api.get("/api/v1/accounts/" + accountId, manager.token()).expect(200).data().get("version")
                .asLong();
        return changeStatus(accountId, status, version);
    }

    private JsonNode changeStatus(String accountId, String status, long version) {
        return api.post("/api/v1/accounts/" + accountId + "/status", manager.token(), Map.of("status", status,
                "reason", "Branch decision", "version", version)).expect(200).data();
    }

    private void assertBooksIntact() {
        ReconciliationReport report = inTenant(tenant.id(), () -> ledgerQueries.reconcile());
        assertThat(report.intact()).as("reconciliation: %s", report).isTrue();
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null)).balanced()).isTrue();
    }

    private BigDecimal feeIncome() {
        String feeGl = inTenant(tenant.id(), () -> chartOfAccounts.requireSystem(SystemAccount.ACCOUNT_FEE_INCOME)
                .code());
        return inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null)).rows().stream()
                .filter(row -> row.code().equals(feeGl))
                .map(TrialBalanceRow::creditBalance)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private long auditCount(String action, String resourceId) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.audit_log WHERE action = :action AND resource_id = :resourceId")
                .param("action", action)
                .param("resourceId", resourceId)
                .query(Long.class)
                .single());
    }

    private long outboxCount(String eventType) {
        return inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.outbox_event WHERE event_type = :eventType")
                .param("eventType", eventType)
                .query(Long.class)
                .single());
    }

    private static Map<String, Object> usd() {
        Map<String, Object> terms = terms("0");
        terms.put("currency", "USD");
        return terms;
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }
}
