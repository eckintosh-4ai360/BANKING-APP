package com.company.banking.ledger.dto;

import com.company.banking.ledger.model.EntryDirection;
import java.math.BigDecimal;
import java.util.UUID;

public record PostedLine(
        int lineNo,
        UUID chartOfAccountId,
        String glCode,
        UUID ledgerAccountId,
        UUID branchId,
        String currency,
        EntryDirection direction,
        BigDecimal amount,
        String narration) {
}
