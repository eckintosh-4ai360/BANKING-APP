package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Exactly one of {@code individual} / {@code business} must be present, matching {@code customerType}. Group
 * customers arrive with group lending (Phase 4).
 */
public record CreateCustomerRequest(
        @NotBlank @Pattern(regexp = "^(INDIVIDUAL|BUSINESS)$") String customerType,
        @NotNull UUID homeBranchId,
        @Pattern(regexp = "^(BRANCH|FIELD|MOBILE|WEB|API)$") String onboardingChannel,
        @Pattern(regexp = ValidationPatterns.PHONE) String primaryPhone,
        @Email @Size(max = 254) String email,
        @Size(max = 10) String preferredLanguage,
        UUID relationshipOfficerId,
        @Valid IndividualDetails individual,
        @Valid BusinessDetails business) {
}
