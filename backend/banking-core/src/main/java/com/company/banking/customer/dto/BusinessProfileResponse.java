package com.company.banking.customer.dto;

import java.time.LocalDate;

public record BusinessProfileResponse(
        String registeredName,
        String tradingName,
        String registrationNumber,
        LocalDate registrationDate,
        String businessType,
        String industrySector,
        String annualTurnoverBand,
        Integer numberOfEmployees,
        String taxIdMasked) {
}
