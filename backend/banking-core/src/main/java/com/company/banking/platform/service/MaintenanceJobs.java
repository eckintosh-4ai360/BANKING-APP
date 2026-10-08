package com.company.banking.platform.service;

import com.company.banking.account.service.AccountHoldService;
import com.company.banking.common.idempotency.IdempotencyService;
import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.persistence.ClusterLock;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantProvisioningService;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background jobs that work tenant by tenant (row-level security means every query runs inside one tenant). Each
 * job runs on one instance at a time (cluster lock) and isolates failures per tenant.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = "banking.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class MaintenanceJobs {

    private static final int PAGE_SIZE = 200;
    private static final int OUTBOX_BATCH = 100;
    private static final int PURGE_BATCH = 1000;
    private static final int HOLD_BATCH = 500;

    private final TenantProvisioningService provisioningService;
    private final OutboxService outboxService;
    private final IdempotencyService idempotencyService;
    private final AccountHoldService holdService;
    private final ClusterLock clusterLock;

    @Scheduled(fixedDelayString = "${banking.outbox.relay-interval:PT5S}")
    public void relayOutbox() {
        clusterLock.runExclusively("job:outbox-relay", () -> forEachTenant("outbox relay",
                tenant -> outboxService.relayPending(OUTBOX_BATCH)));
    }

    @Scheduled(fixedDelayString = "${banking.idempotency.purge-interval:PT1H}")
    public void purgeExpiredIdempotencyRecords() {
        clusterLock.runExclusively("job:idempotency-purge", () -> forEachTenant("idempotency purge",
                tenant -> idempotencyService.purgeExpired(PURGE_BATCH)));
    }

    @Scheduled(fixedDelayString = "${banking.holds.expiry-interval:PT5M}")
    public void expireHolds() {
        clusterLock.runExclusively("job:hold-expiry", () -> forEachTenant("hold expiry",
                tenant -> holdService.expireDue(HOLD_BATCH)));
    }

    private void forEachTenant(String job, Consumer<TenantSummary> work) {
        int page = 0;
        Page<TenantSummary> tenants;
        do {
            tenants = provisioningService.list(PageRequest.of(page++, PAGE_SIZE, Sort.by("code")));
            for (TenantSummary tenant : tenants) {
                try {
                    TenantContext.runAs(tenant.id(), () -> CurrentActor.callAsSystem(tenant.id(), () -> {
                        work.accept(tenant);
                        return null;
                    }));
                } catch (RuntimeException failure) {
                    log.error("{} failed for tenant {}: {}", job, tenant.code(), failure.getClass().getSimpleName());
                }
            }
        } while (tenants.hasNext());
    }
}
