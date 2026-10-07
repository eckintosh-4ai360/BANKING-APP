package com.company.banking.tenant.dto;

import java.util.Set;
import java.util.UUID;

/**
 * Input for provisioning a tenant (issued by the platform onboarding flow, already validated).
 */
public record NewTenantCommand(
        UUID tenantId,
        String code,
        String legalName,
        String displayName,
        String institutionType,
        String countryCode,
        String baseCurrency,
        String timezone,
        String locale,
        String licenceNumber,
        String contactEmail,
        String contactPhone,
        Set<String> licensedFeatures) {
}
