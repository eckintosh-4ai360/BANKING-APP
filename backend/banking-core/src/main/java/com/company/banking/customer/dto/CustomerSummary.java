package com.company.banking.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerSummary(
        UUID id,
        String customerNumber,
        String customerType,
        String displayName,
        String primaryPhone,
        String status,
        String kycStatus,
        String kycTierCode,
        String riskLevel,
        UUID homeBranchId,
        Instant createdAt) {
}
