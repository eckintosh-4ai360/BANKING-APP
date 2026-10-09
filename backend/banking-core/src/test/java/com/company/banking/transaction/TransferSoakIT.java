package com.company.banking.transaction;

import static com.company.banking.support.ProductRequests.terms;
import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.support.Fixtures.StaffHandle;
import com.company.banking.support.Fixtures.TenantHandle;
import com.company.banking.transaction.dto.TransferRequest;
import com.company.banking.transaction.service.TransactionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Phase 2 exit gate: many concurrent transfers between a small set of accounts across two branches must leave the
 * ledger intact (every journal balanced, every balance equal to its entries, money neither created nor lost).
 * Runs 1,000 transfers by default; {@code -Dsoak.transfers=10000} runs the full gate.
 */
class TransferSoakIT extends LedgerIntegrationTest {

    private static final int ACCOUNTS = 20;
    private static final BigDecimal FUNDING = new BigDecimal("10000.00");

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private JdbcClient jdbcClient;

    @Test
    void concurrentTransfersLeaveTheLedgerIntact() throws Exception {
        int transfers = Integer.getInteger("soak.transfers", 1_000);
        TenantHandle tenant = fixtures.onboardTenant();
        UUID kumasi = fixtures.createBranch(tenant, "KUM");
        StaffHandle officer = fixtures.createStaff(tenant, "officer", tenant.headOfficeId(), true, "LOAN_OFFICER");
        StaffHandle manager = fixtures.createStaff(tenant, "manager", tenant.headOfficeId(), true, "BRANCH_MANAGER");
        String product = fixtures.publishedProduct(tenant, "CURR01", "CURRENT", terms("0"));
        List<UUID> accounts = new ArrayList<>();
        for (int i = 0; i < ACCOUNTS; i++) {
            UUID branch = i % 2 == 0 ? tenant.headOfficeId() : kumasi;
            UUID customer = fixtures.verifiedIndividual(officer, manager, branch, "Customer" + i);
            accounts.add(UUID.fromString(fixtures.openAccount(manager.token(), customer, product).get("id")
                    .asString()));
        }
        for (UUID account : accounts) {
            // Funded straight through the ledger: the soak is about transfers, not tellers.
            UUID ledgerAccount = inTenant(tenant.id(), () -> jdbcClient.sql(
                            "SELECT ledger_account_id FROM core.account WHERE id = :id")
                    .param("id", account).query(UUID.class).single());
            deposit(tenant.id(), ledgerAccount, tenant.headOfficeId(), "GHS", FUNDING.toPlainString());
        }

        Random random = new Random(20261008L);
        List<TransferRequest> requests = new ArrayList<>(transfers);
        for (int i = 0; i < transfers; i++) {
            int from = random.nextInt(ACCOUNTS);
            int to = (from + 1 + random.nextInt(ACCOUNTS - 1)) % ACCOUNTS;
            BigDecimal amount = BigDecimal.valueOf(1 + random.nextInt(5_000), 2);
            requests.add(new TransferRequest(accounts.get(from), accounts.get(to), amount, null, null));
        }
        AtomicInteger posted = new AtomicInteger();
        AtomicInteger refused = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(16);
        List<Future<?>> futures = new ArrayList<>(transfers);
        long started = System.nanoTime();
        for (TransferRequest request : requests) {
            futures.add(pool.submit(() -> {
                try {
                    inTenant(tenant.id(), () -> transactionService.transfer(UUID.randomUUID().toString(), request));
                    posted.incrementAndGet();
                } catch (BankingException refusal) {
                    assertThat(refusal.getErrorCode().code()).isEqualTo("INSUFFICIENT_FUNDS");
                    refused.incrementAndGet();
                }
                return null;
            }));
        }
        for (Future<?> future : futures) {
            future.get();
        }
        pool.shutdown();
        long millis = (System.nanoTime() - started) / 1_000_000;
        System.out.printf("Soak: %d transfers (%d posted, %d refused for funds) in %d ms%n", transfers,
                posted.get(), refused.get(), millis);

        ReconciliationReport report = inTenant(tenant.id(), () -> ledgerQueries.reconcile());
        assertThat(report.balanceBreaks()).isEmpty();
        assertThat(report.unbalancedJournals()).isEmpty();
        assertThat(report.intact()).isTrue();
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.trialBalance(null, "GHS", null)).balanced()).isTrue();
        BigDecimal total = BigDecimal.ZERO;
        for (UUID account : accounts) {
            BigDecimal balance = new BigDecimal(api.get("/api/v1/accounts/" + account, manager.token()).expect(200)
                    .data().get("ledgerBalance").asString());
            assertThat(balance.signum()).as("never overdrawn").isGreaterThanOrEqualTo(0);
            total = total.add(balance);
        }
        assertThat(total).as("money is neither created nor lost")
                .isEqualByComparingTo(FUNDING.multiply(BigDecimal.valueOf(ACCOUNTS)));
        long recorded = inTenant(tenant.id(), () -> jdbcClient.sql(
                        "SELECT count(*) FROM core.financial_transaction WHERE transaction_type = 'TRANSFER'")
                .query(Long.class)
                .single());
        assertThat(recorded).isEqualTo(posted.get());
    }
}
