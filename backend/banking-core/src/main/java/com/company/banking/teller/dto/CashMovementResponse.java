package com.company.banking.teller.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CashMovementResponse(
        UUID id,
        String reference,
        String movementType,
        String status,
        String currency,
        BigDecimal amount,
        UUID fromBranchId,
        UUID toBranchId,
        UUID fromVaultId,
        UUID fromDrawerId,
        UUID toVaultId,
        UUID toDrawerId,
        String note,
        UUID requestedBy,
        Instant requestedAt,
        UUID approvedBy,
        Instant approvedAt,
        UUID receivedBy,
        Instant receivedAt,
        String rejectionReason,
        Long version) {
}
