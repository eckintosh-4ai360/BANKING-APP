package com.company.banking.account.service;

import com.company.banking.account.entity.Account;
import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.repository.AccountRepository;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.eod.EndOfDayContext;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.product.dto.ProductTerms;
import com.company.banking.product.service.ProductService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

/**
 * Marks active accounts dormant when the customer has not used them for the product's dormancy period (customer
 * transactions count; interest and charges do not). Dormant accounts accept money but pay nothing out until a
 * supervisor reactivates them.
 */
@Service
@RequiredArgsConstructor
public class DormancyService {

    static final String STEP = "DORMANCY";
    private static final int BATCH = 500;
    private static final UUID FIRST = new UUID(0, 0);

    private final AccountRepository accounts;
    private final ProductService productService;
    private final AuditService auditService;
    private final Clock clock;

    public Map<String, Object> run(EndOfDayContext context) {
        UUID tenantId = TenantContext.requireTenantId();
        int dormant = 0;
        UUID after = FIRST;
        while (true) {
            UUID cursor = after;
            List<UUID> batch = context.inTransaction(() -> accounts.idsWithStatus(tenantId, AccountStatus.ACTIVE,
                    cursor, Limit.of(BATCH)));
            if (batch.isEmpty()) {
                break;
            }
            dormant += context.inTransaction(() -> {
                Map<UUID, ProductTerms> terms = new HashMap<>();
                int count = 0;
                for (UUID accountId : batch) {
                    count += markIfInactive(tenantId, accountId, context.businessDate(), terms) ? 1 : 0;
                }
                return count;
            });
            after = batch.getLast();
            context.checkpoint(STEP, "batch");
        }
        return Map.of("dormant", dormant);
    }

    private boolean markIfInactive(UUID tenantId, UUID accountId, LocalDate closed,
                                   Map<UUID, ProductTerms> termsCache) {
        Account account = accounts.lockByTenantIdAndId(tenantId, accountId).orElseThrow();
        if (account.getStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        ProductTerms terms = termsCache.computeIfAbsent(account.getProductVersionId(), productService::terms);
        LocalDate lastActive = account.getLastActivityOn() != null ? account.getLastActivityOn()
                : account.getOpenedOn();
        if (lastActive.plusDays(terms.dormancyDays()).isAfter(closed)) {
            return false;
        }
        account.changeStatus(AccountStatus.DORMANT, "No customer activity since " + lastActive, clock.instant());
        accounts.saveAndFlush(account);
        auditService.record(AuditEvent.builder("ACCOUNT_DORMANT", AccountService.RESOURCE)
                .resourceId(accountId)
                .resourceReference(account.getAccountNumber())
                .branchId(account.getBranchId())
                .before(Map.of("status", AccountStatus.ACTIVE))
                .after(Map.of("status", AccountStatus.DORMANT))
                .metadata("lastActivity", lastActive.toString())
                .build());
        return true;
    }
}
