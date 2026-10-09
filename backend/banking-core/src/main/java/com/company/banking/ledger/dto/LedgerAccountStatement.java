package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Entries of a sub-ledger account over a period with running balances, straight from the ledger. Balances are in
 * the account's normal-side terms (for a deposit, credits raise the balance).
 *
 * @param openingBalance balance at the end of the day before {@code from}
 * @param closingBalance balance at the end of {@code to}: opening plus the movements listed
 */
public record LedgerAccountStatement(
        UUID ledgerAccountId,
        String currency,
        LocalDate from,
        LocalDate to,
        BigDecimal openingBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal closingBalance,
        List<Line> lines) {

    /**
     * @param reference the business reference (e.g. the transaction reference) of the journal
     */
    public record Line(
            LocalDate businessDate,
            LocalDate valueDate,
            Instant postedAt,
            String journalNumber,
            String sourceType,
            String reference,
            String narration,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal balance) {
    }
}
