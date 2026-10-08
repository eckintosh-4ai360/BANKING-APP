package com.company.banking.ledger;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.error.BankingException;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.ReversalRequest;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.support.Fixtures.TenantHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Spec §55 / risk F3: concurrent requests must never double-spend, deadlock or reverse twice.
 */
class LedgerConcurrencyIT extends LedgerIntegrationTest {

    private TenantHandle tenant;
    private UUID branch;

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
        branch = tenant.headOfficeId();
    }

    @Test
    void concurrentWithdrawalsNeverOverdraw() throws Exception {
        UUID account = openDepositAccount(tenant.id(), branch, "GHS", "Shared account");
        deposit(tenant.id(), account, branch, "GHS", "1000.00");

        List<Outcome> outcomes = runConcurrently(20, index -> withdraw(tenant.id(), account, branch, "GHS", "100.00"));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(10);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()))
                .allSatisfy(outcome -> assertThat(outcome.errorCode()).isEqualTo(LedgerErrorCode.INSUFFICIENT_FUNDS));
        assertThat(balanceOf(tenant.id(), account)).isZero();
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void opposingTransfersNeitherDeadlockNorLoseMoney() throws Exception {
        UUID a = openDepositAccount(tenant.id(), branch, "GHS", "A");
        UUID b = openDepositAccount(tenant.id(), branch, "GHS", "B");
        deposit(tenant.id(), a, branch, "GHS", "1000.00");
        deposit(tenant.id(), b, branch, "GHS", "1000.00");

        List<Outcome> outcomes = runConcurrently(40, index -> index % 2 == 0
                ? transfer(tenant.id(), branch, a, b, "10.00")
                : transfer(tenant.id(), branch, b, a, "10.00"));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.succeeded())
                .as("no deadlock or lock timeout: %s", outcome.unexpected()).isTrue());
        assertThat(balanceOf(tenant.id(), a).add(balanceOf(tenant.id(), b))).isEqualByComparingTo("2000.00");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).intact()).isTrue();
    }

    @Test
    void aJournalIsReversedOnceEvenWhenManyTryAtOnce() throws Exception {
        UUID account = openDepositAccount(tenant.id(), branch, "GHS", "Account");
        PostedJournal original = deposit(tenant.id(), account, branch, "GHS", "60.00");

        List<Outcome> outcomes = runConcurrently(8, index -> inTenant(tenant.id(),
                () -> postingEngine.reverse(new ReversalRequest(original.id(), "Duplicate request " + index, null, null))));

        assertThat(outcomes.stream().filter(Outcome::succeeded)).hasSize(1);
        assertThat(outcomes.stream().filter(outcome -> !outcome.succeeded()))
                .allSatisfy(outcome -> assertThat(outcome.errorCode()).isEqualTo(LedgerErrorCode.JOURNAL_ALREADY_REVERSED));
        assertThat(balanceOf(tenant.id(), account)).isZero();
    }

    private record Outcome(boolean succeeded, Object errorCode, Throwable unexpected) {
    }

    @FunctionalInterface
    private interface Task {
        Object run(int index);
    }

    /**
     * Starts all tasks at the same moment on separate threads (each in its own database transaction).
     */
    private List<Outcome> runConcurrently(int tasks, Task task) throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(tasks, 8));
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int index = 0; index < tasks; index++) {
                int taskIndex = index;
                Callable<Object> callable = () -> {
                    start.await();
                    return task.run(taskIndex);
                };
                futures.add(executor.submit(callable));
            }
            start.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) {
                try {
                    future.get(60, TimeUnit.SECONDS);
                    outcomes.add(new Outcome(true, null, null));
                } catch (ExecutionException failure) {
                    Throwable cause = failure.getCause();
                    if (cause instanceof BankingException banking) {
                        outcomes.add(new Outcome(false, banking.getErrorCode(), null));
                    } else {
                        outcomes.add(new Outcome(false, null, cause));
                    }
                } catch (java.util.concurrent.TimeoutException timeout) {
                    outcomes.add(new Outcome(false, null, timeout));
                }
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }
}
