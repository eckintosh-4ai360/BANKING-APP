package com.company.banking.customer.dto;

public record IdentificationTypeResponse(
        String code,
        String name,
        String appliesTo,
        String formatRegex,
        String formatHint,
        boolean requiresExpiry,
        boolean supportsElectronicVerification,
        boolean active,
        int sortOrder,
        Long version) {
}
