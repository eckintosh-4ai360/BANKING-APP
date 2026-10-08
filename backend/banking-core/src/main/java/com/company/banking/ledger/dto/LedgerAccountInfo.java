package com.company.banking.ledger.dto;

import java.util.UUID;

public record LedgerAccountInfo(
        UUID id,
        UUID chartOfAccountId,
        String glCode,
        UUID branchId,
        String currency,
        String type,
        String normalSide,
        boolean balanceCheck,
        String name,
        String ownerType,
        UUID ownerId,
        String status) {
}
