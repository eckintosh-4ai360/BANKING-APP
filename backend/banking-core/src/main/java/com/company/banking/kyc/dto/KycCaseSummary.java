package com.company.banking.kyc.dto;

import java.time.Instant;
import java.util.UUID;

public record KycCaseSummary(
        UUID id,
        UUID customerId,
        String customerNumber,
        String customerName,
        UUID branchId,
        String caseType,
        String targetTierCode,
        String status,
        UUID submittedBy,
        Instant submittedAt,
        Instant createdAt) {
}
