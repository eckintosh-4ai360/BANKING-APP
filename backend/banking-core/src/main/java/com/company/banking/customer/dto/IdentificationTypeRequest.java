package com.company.banking.customer.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param code    required when creating; ignored on update (the path identifies the type)
 * @param version required when updating
 */
public record IdentificationTypeRequest(
        @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,29}$") String code,
        @NotBlank @Size(max = 100) String name,
        @NotBlank @Pattern(regexp = "^(INDIVIDUAL|BUSINESS|ANY)$") String appliesTo,
        @Size(max = 200) String formatRegex,
        @Size(max = 100) String formatHint,
        boolean requiresExpiry,
        boolean supportsElectronicVerification,
        boolean active,
        @Min(0) int sortOrder,
        Long version) {
}
