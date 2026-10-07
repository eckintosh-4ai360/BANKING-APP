package com.company.banking.kyc.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record KycCheckResponse(
        UUID id,
        String checkType,
        String method,
        String provider,
        String result,
        BigDecimal score,
        String providerReference,
        String note,
        UUID performedBy,
        Instant performedAt) {
}
