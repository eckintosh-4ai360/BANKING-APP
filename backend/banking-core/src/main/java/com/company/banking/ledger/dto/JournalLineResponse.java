package com.company.banking.ledger.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record JournalLineResponse(
        int lineNo,
        UUID chartOfAccountId,
        String glCode,
        String glName,
        UUID ledgerAccountId,
        String ledgerAccountName,
        UUID branchId,
        String currency,
        String direction,
        BigDecimal amount,
        String narration) {
}
