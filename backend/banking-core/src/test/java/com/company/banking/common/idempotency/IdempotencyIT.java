package com.company.banking.common.idempotency;

import static org.assertj.core.api.Assertions.assertThat;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.idempotency.IdempotencyService.Request;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.ledger.LedgerIntegrationTest;
import com.company.banking.ledger.dto.PostedJournal;
import com.company.banking.ledger.dto.PostingLine;
import com.company.banking.ledger.dto.PostingRequest;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.JournalSource;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.support.Fixtures.TenantHandle;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class IdempotencyIT extends LedgerIntegrationTest {

    @Autowired
    private IdempotencyService idempotency;

    private TenantHandle tenant;

    record Receipt(UUID id, String note) {
    }

    @BeforeEach
    void setUp() {
        tenant = fixtures.onboardTenant();
    }

    @Test
    void aRetryGetsTheStoredResponseAndTheActionRunsOnce() {
        AtomicInteger runs = new AtomicInteger();
        String key = newKey();

        Result<Receipt> first = run("TEST:SCOPE", key, Map.of("amount", "10.00"), () -> {
            runs.incrementAndGet();
            return new Receipt(UUID.randomUUID(), "first");
        });
        Result<Receipt> retry = run("TEST:SCOPE", key, Map.of("amount", "10.00"), () -> {
            runs.incrementAndGet();
            return new Receipt(UUID.randomUUID(), "second");
        });

        assertThat(first.replayed()).isFalse();
        assertThat(retry.replayed()).isTrue();
        assertThat(retry.response()).isEqualTo(first.response());
        assertThat(runs).hasValue(1);
    }

    @Test
    void anIdempotentRetryOfADepositPostsOnce() {
        UUID account = openDepositAccount(tenant.id(), tenant.headOfficeId(), "GHS", "Account");
        String key = newKey();
        Supplier<Result<Receipt>> depositOnce = () -> inTenant(tenant.id(), () -> idempotency.execute(
                new Request("TEST:DEPOSIT", key, idempotency.hash(Map.of("account", account, "amount", "75.00")), "JOURNAL"),
                Receipt.class, Receipt::id, () -> {
                    PostedJournal journal = postingEngine.post(new PostingRequest(JournalSource.TRANSACTION, "DEP-1",
                            null, tenant.headOfficeId(), null, "Deposit", null, List.of(
                            PostingLine.toSystemAccount(SystemAccount.CASH_AT_BRANCH, tenant.headOfficeId(), "GHS",
                                    EntryDirection.DEBIT, money("75.00"), null),
                            PostingLine.toLedgerAccount(account, EntryDirection.CREDIT, money("75.00"), null))));
                    return new Receipt(journal.id(), journal.journalNumber());
                }));

        Result<Receipt> first = depositOnce.get();
        Result<Receipt> second = depositOnce.get();
        Result<Receipt> third = depositOnce.get();

        assertThat(second.response()).isEqualTo(first.response());
        assertThat(third.replayed()).isTrue();
        assertThat(balanceOf(tenant.id(), account)).isEqualByComparingTo("75.00");
        assertThat(inTenant(tenant.id(), () -> ledgerQueries.reconcile()).journalsChecked()).isEqualTo(1);
    }

    @Test
    void reusingAKeyForADifferentRequestIsRefused() {
        String key = newKey();
        run("TEST:SCOPE", key, Map.of("amount", "10.00"), () -> new Receipt(UUID.randomUUID(), "x"));

        assertFailsWith(CommonErrorCode.IDEMPOTENCY_KEY_REUSED, () -> run("TEST:SCOPE", key,
                Map.of("amount", "10.01"), () -> new Receipt(UUID.randomUUID(), "y")));
        // The same key in another scope (another user or operation) is unrelated.
        assertThat(run("TEST:OTHER", key, Map.of("amount", "10.01"), () -> new Receipt(UUID.randomUUID(), "z"))
                .replayed()).isFalse();
    }

    @Test
    void aRequestThatFailedCanBeRetried() {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();
        assertFailsWith(CommonErrorCode.BUSINESS_RULE_VIOLATION, () -> run("TEST:SCOPE", key, Map.of("n", 1), () -> {
            runs.incrementAndGet();
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION);
        }));
        Result<Receipt> retry = run("TEST:SCOPE", key, Map.of("n", 1), () -> {
            runs.incrementAndGet();
            return new Receipt(UUID.randomUUID(), "ok");
        });
        assertThat(retry.replayed()).isFalse();
        assertThat(runs).hasValue(2);
    }

    @Test
    void concurrentDuplicatesRunTheActionOnce() throws Exception {
        String key = newKey();
        AtomicInteger runs = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(6);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Result<Receipt>>> futures = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    return run("TEST:SCOPE", key, Map.of("amount", "5.00"), () -> {
                        runs.incrementAndGet();
                        sleep(300);
                        return new Receipt(UUID.randomUUID(), "only");
                    });
                }));
            }
            start.countDown();
            List<Result<Receipt>> results = new ArrayList<>();
            for (Future<Result<Receipt>> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            assertThat(runs).hasValue(1);
            assertThat(results).extracting(Result::response).containsOnly(results.get(0).response());
            assertThat(results.stream().filter(result -> !result.replayed())).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keysMustBeWellFormed() {
        assertFailsWith(CommonErrorCode.IDEMPOTENCY_KEY_REQUIRED, () -> IdempotencyService.requireValidKey(null));
        assertFailsWith(CommonErrorCode.INVALID_IDEMPOTENCY_KEY, () -> IdempotencyService.requireValidKey("short"));
        assertFailsWith(CommonErrorCode.INVALID_IDEMPOTENCY_KEY, () -> IdempotencyService.requireValidKey("has spaces in it"));
        IdempotencyService.requireValidKey(UUID.randomUUID().toString());
    }

    @Test
    void theRequestHashIgnoresFieldOrder() {
        Map<String, Object> ab = new LinkedHashMap<>();
        ab.put("a", "1");
        ab.put("b", Map.of("y", 2, "x", 1));
        Map<String, Object> ba = new LinkedHashMap<>();
        ba.put("b", Map.of("x", 1, "y", 2));
        ba.put("a", "1");
        assertThat(idempotency.hash(ab)).isEqualTo(idempotency.hash(ba)).hasSize(64);
        assertThat(idempotency.hash(Map.of("a", "2"))).isNotEqualTo(idempotency.hash(ab));
    }

    private Result<Receipt> run(String scope, String key, Object request, Supplier<Receipt> action) {
        return inTenant(tenant.id(), () -> idempotency.execute(
                new Request(scope, key, idempotency.hash(request), "TEST"), Receipt.class, Receipt::id, action));
    }

    private static String newKey() {
        return UUID.randomUUID().toString();
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
