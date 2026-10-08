package com.company.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The result of a money movement: the transaction and the balances it left behind. A retried request (same
 * Idempotency-Key) gets this same response again, balances as they were right after the original posting.
 */
public record PostedTransactionResponse(TransactionResponse transaction, List<BalanceAfter> balances) {

    public record BalanceAfter(UUID accountId, String accountNumber, BigDecimal ledgerBalance,
                               BigDecimal availableBalance) {
    }
}
