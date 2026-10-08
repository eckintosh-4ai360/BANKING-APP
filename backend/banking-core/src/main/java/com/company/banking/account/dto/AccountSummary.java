package com.company.banking.account.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record AccountSummary(
        UUID id,
        String accountNumber,
        String title,
        UUID customerId,
        String productCode,
        String productType,
        UUID branchId,
        String currency,
        String status,
        BigDecimal ledgerBalance,
        BigDecimal availableBalance,
        LocalDate openedOn) {
}
