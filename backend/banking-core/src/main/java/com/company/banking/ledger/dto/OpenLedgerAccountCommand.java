package com.company.banking.ledger.dto;

import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.SystemAccount;
import java.util.UUID;

/**
 * Opens a sub-ledger account under a GL account (by id, or by system code when {@code chartOfAccountId} is null).
 *
 * @param balanceCheck true when the account may never go below zero plus its overdraft limit (customer deposits);
 *                     false for hot settlement and suspense accounts
 * @param ownerType    what owns the account (e.g. {@code ACCOUNT}, {@code STAFF}); unique per owner
 */
public record OpenLedgerAccountCommand(
        UUID chartOfAccountId,
        SystemAccount systemAccount,
        UUID branchId,
        String currency,
        LedgerAccountType type,
        boolean balanceCheck,
        String name,
        String ownerType,
        UUID ownerId) {
}
