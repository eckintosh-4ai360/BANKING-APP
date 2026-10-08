package com.company.banking.account.dto;

import java.util.UUID;

/**
 * An account as the transaction module sees it while moving money: locked, in the caller's branch scope, with
 * what the posting needs.
 *
 * @param creditAllowed the status lets money in (PENDING, ACTIVE, RESTRICTED, DORMANT)
 * @param debitAllowed  the status lets money out (ACTIVE only)
 */
public record PostingAccount(
        UUID id,
        String accountNumber,
        String title,
        UUID customerId,
        UUID ledgerAccountId,
        UUID branchId,
        String currency,
        String status,
        UUID productVersionId,
        boolean creditAllowed,
        boolean debitAllowed) {
}
