package com.company.banking.account.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record HoldResponse(
        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String holdType,
        String status,
        String reason,
        String reference,
        Instant expiresAt,
        Instant placedAt,
        UUID placedBy,
        Instant releasedAt,
        UUID releasedBy,
        String releaseReason,
        Long version) {
}
