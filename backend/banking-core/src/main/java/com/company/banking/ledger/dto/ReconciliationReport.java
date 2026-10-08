package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ledger integrity check: every balance projection equals the sum of its entries, and every journal balances.
 */
public record ReconciliationReport(
        Instant checkedAt,
        long ledgerAccountsChecked,
        long journalsChecked,
        List<BalanceBreak> balanceBreaks,
        List<String> unbalancedJournals,
        boolean intact) {

    public record BalanceBreak(UUID ledgerAccountId, BigDecimal projected, BigDecimal fromEntries) {
    }
}
