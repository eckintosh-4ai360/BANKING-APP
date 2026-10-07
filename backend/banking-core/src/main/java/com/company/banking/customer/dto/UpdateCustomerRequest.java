package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Contact details can always change. Identity data (names, date of birth, registration, tax number) is locked once
 * KYC is verified or under review; an identity change goes through a KYC update case.
 */
public record UpdateCustomerRequest(
        @Pattern(regexp = ValidationPatterns.PHONE) String primaryPhone,
        @Email @Size(max = 254) String email,
        @Size(max = 10) String preferredLanguage,
        UUID relationshipOfficerId,
        @Valid IndividualDetails individual,
        @Valid BusinessDetails business,
        @NotNull Long version) {
}
