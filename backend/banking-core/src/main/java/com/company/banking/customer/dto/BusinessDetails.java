package com.company.banking.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record BusinessDetails(
        @NotBlank @Size(max = 200) String registeredName,
        @Size(max = 200) String tradingName,
        @NotBlank @Size(max = 50) String registrationNumber,
        @PastOrPresent LocalDate registrationDate,
        @NotBlank
        @Pattern(regexp = "^(SOLE_PROPRIETORSHIP|PARTNERSHIP|LIMITED_COMPANY|COOPERATIVE|NGO|ASSOCIATION|OTHER)$")
        String businessType,
        @Size(max = 100) String industrySector,
        @Size(max = 30) String annualTurnoverBand,
        @PositiveOrZero Integer numberOfEmployees,
        @Size(min = 5, max = 30) String taxId) {

    @Override
    public String toString() {
        return "BusinessDetails[registeredName=" + registeredName + ", taxId=***]";
    }
}
