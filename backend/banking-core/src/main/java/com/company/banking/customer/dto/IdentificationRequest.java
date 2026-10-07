package com.company.banking.customer.dto;

import com.company.banking.common.validation.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record IdentificationRequest(
        @NotBlank @Size(max = 30) String idTypeCode,
        @NotBlank @Size(max = 50) String idNumber,
        @Pattern(regexp = ValidationPatterns.COUNTRY_CODE) String issuingCountry,
        @PastOrPresent LocalDate issueDate,
        LocalDate expiryDate,
        boolean primary) {

    @Override
    public String toString() {
        return "IdentificationRequest[idTypeCode=" + idTypeCode + ", idNumber=***]";
    }
}
