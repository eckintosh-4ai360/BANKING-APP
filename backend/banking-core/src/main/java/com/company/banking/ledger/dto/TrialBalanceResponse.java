package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Trial balance in one currency as of the end of a business date.
 *
 * @param balanced total debit balances equal total credit balances (always true for an intact ledger)
 */
public record TrialBalanceResponse(
        LocalDate asOf,
        String currency,
        UUID branchId,
        List<TrialBalanceRow> rows,
        BigDecimal totalDebitBalance,
        BigDecimal totalCreditBalance,
        boolean balanced) {
}
