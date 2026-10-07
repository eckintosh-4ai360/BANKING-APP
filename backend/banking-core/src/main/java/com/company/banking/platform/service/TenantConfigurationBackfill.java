package com.company.banking.platform.service;

import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.service.IdentificationTypeService;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * Gives institutions onboarded before a configuration area existed its default configuration (identification
 * types, KYC tiers). Idempotent: institutions that already have configuration are left untouched.
 */
@Order(1)
@Component
@RequiredArgsConstructor
public class TenantConfigurationBackfill implements ApplicationRunner {

    private static final int PAGE_SIZE = 200;

    private final TenantProvisioningService provisioningService;
    private final IdentificationTypeService identificationTypeService;
    private final KycTierService kycTierService;

    @Override
    public void run(ApplicationArguments args) {
        int page = 0;
        Page<TenantSummary> tenants;
        do {
            tenants = provisioningService.list(PageRequest.of(page++, PAGE_SIZE, Sort.by("code")));
            tenants.forEach(this::backfill);
        } while (tenants.hasNext());
    }

    private void backfill(TenantSummary tenant) {
        TenantContext.runAs(tenant.id(), () -> CurrentActor.callAsSystem(tenant.id(), () -> {
            identificationTypeService.provisionDefaults(tenant.countryCode());
            kycTierService.provisionDefaults();
            return null;
        }));
    }
}
