package com.company.banking.tenant.dto;

import java.util.UUID;

/**
 * Public view of a tenant for other modules and API responses.
 */
public record TenantSummary(
        UUID id,
        String code,
        String legalName,
        String displayName,
        String institutionType,
        String status,
        String countryCode,
        String baseCurrency,
        String timezone,
        String locale,
        String licenceNumber) {

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }
}
