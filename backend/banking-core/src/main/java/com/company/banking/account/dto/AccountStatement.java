package com.company.banking.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * An account statement built from the ledger: every entry on the account in the period (charges and reversals
 * included) with the running balance. Debits take money out of the account, credits put money in.
 */
public record AccountStatement(
        String institutionName,
        UUID accountId,
        String accountNumber,
        String accountTitle,
        List<String> holders,
        String productName,
        String branchName,
        String currency,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance,
        Instant generatedAt,
        List<Line> lines) {

    public record Line(
            LocalDate date,
            LocalDate valueDate,
            String reference,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance) {
    }
}
