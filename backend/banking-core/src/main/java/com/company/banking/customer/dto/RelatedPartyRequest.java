package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param version required when updating an existing record
 */
public record RelatedPartyRequest(
        @NotBlank @Size(max = 200) String fullName,
        @NotBlank
        @Pattern(regexp = "^(DIRECTOR|SHAREHOLDER|BENEFICIAL_OWNER|AUTHORISED_SIGNATORY|PARTNER|TRUSTEE)$")
        String role,
        @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2) BigDecimal ownershipPercent,
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String nationality,
        @Past LocalDate dateOfBirth,
        @Pattern(regexp = ValidationPatterns.PHONE) String phone,
        @Email @Size(max = 254) String email,
        @Size(max = 30) String idTypeCode,
        @Size(max = 50) String idNumber,
        boolean politicallyExposed,
        UUID relatedCustomerId,
        Long version) {

    @Override
    public String toString() {
        return "RelatedPartyRequest[role=" + role + ", idNumber=***]";
    }
}
