package com.company.banking.ledger.dto;

import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.SystemAccount;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * One line of a posting. Exactly one target is set:
 * <ul>
 *   <li>{@code ledgerAccountId}: a sub-ledger account; its GL account, branch and currency are used;</li>
 *   <li>{@code chartOfAccountId} or {@code systemAccount}: a GL account directly, in {@code branchId} and
 *   {@code currency}.</li>
 * </ul>
 * The amount is always positive and already in the currency's minor units; the engine never rounds.
 */
public record PostingLine(
        UUID ledgerAccountId,
        UUID chartOfAccountId,
        SystemAccount systemAccount,
        UUID branchId,
        String currency,
        EntryDirection direction,
        BigDecimal amount,
        String narration) {

    public static PostingLine toLedgerAccount(UUID ledgerAccountId, EntryDirection direction, BigDecimal amount,
                                              String narration) {
        return new PostingLine(ledgerAccountId, null, null, null, null, direction, amount, narration);
    }

    public static PostingLine toGl(UUID chartOfAccountId, UUID branchId, String currency, EntryDirection direction,
                                   BigDecimal amount, String narration) {
        return new PostingLine(null, chartOfAccountId, null, branchId, currency, direction, amount, narration);
    }

    public static PostingLine toSystemAccount(SystemAccount account, UUID branchId, String currency,
                                              EntryDirection direction, BigDecimal amount, String narration) {
        return new PostingLine(null, null, account, branchId, currency, direction, amount, narration);
    }
}
