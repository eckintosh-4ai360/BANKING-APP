package com.company.banking.ledger.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The result of a posting: the journal, its lines (including any inter-branch lines the engine added) and the
 * new balances of every sub-ledger account it touched.
 */
public record PostedJournal(
        UUID id,
        String journalNumber,
        LocalDate businessDate,
        List<PostedLine> lines,
        Map<UUID, BalanceSnapshot> balances) {

    public BalanceSnapshot balanceOf(UUID ledgerAccountId) {
        return balances.get(ledgerAccountId);
    }
}
