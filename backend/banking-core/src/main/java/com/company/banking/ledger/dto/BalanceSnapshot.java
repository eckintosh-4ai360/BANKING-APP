package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A sub-ledger account's balance, in the account's normal-side terms (a positive deposit balance means the
 * institution owes the customer).
 */
public record BalanceSnapshot(
        UUID ledgerAccountId,
        String currency,
        BigDecimal ledgerBalance,
        BigDecimal holdAmount,
        BigDecimal availableBalance,
        BigDecimal overdraftLimit) {
}
