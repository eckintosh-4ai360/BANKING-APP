package com.company.banking.transaction.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash a field officer took for a customer's account, to be credited from the cash the officer carries.
 *
 * @param collectorLedgerAccountId the officer's cash-with-collector ledger account (debited)
 * @param branchId                 the officer's branch
 * @param externalReference        the collection's client reference, kept on the transaction for tracing
 */
public record FieldCollectionCommand(UUID accountId, BigDecimal amount, String currency, UUID collectorLedgerAccountId,
                                     UUID branchId, String narration, String externalReference) {
}
