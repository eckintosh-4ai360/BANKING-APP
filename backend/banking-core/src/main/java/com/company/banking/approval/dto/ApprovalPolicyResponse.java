package com.company.banking.approval.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ApprovalPolicyResponse(
        String requestType,
        String currency,
        BigDecimal thresholdAmount,
        boolean active,
        Instant updatedAt,
        UUID updatedBy) {
}
