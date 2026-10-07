package com.company.banking.kyc.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record KycCaseResponse(
        UUID id,
        UUID customerId,
        String customerNumber,
        String customerName,
        String customerKycStatus,
        UUID branchId,
        String caseType,
        String targetTierCode,
        String status,
        UUID openedBy,
        UUID submittedBy,
        Instant submittedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote,
        String assignedRiskLevel,
        List<RequirementStatus> requirements,
        List<KycCheckResponse> checks,
        Instant createdAt,
        Long version) {
}
