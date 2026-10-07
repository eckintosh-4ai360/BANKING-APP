package com.company.banking.tenant.dto;

import java.util.List;

/**
 * Non-sensitive branding used by white-label apps before login.
 */
public record PublicBrandingResponse(
        String code,
        String displayName,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String supportEmail,
        String supportPhone,
        String baseCurrency,
        String locale,
        List<String> enabledFeatures) {
}
