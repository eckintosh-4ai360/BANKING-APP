package com.company.banking.product.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductVersionResponse(
        UUID id,
        int versionNo,
        String status,
        String currency,
        UUID depositGlId,
        UUID feeIncomeGlId,
        UUID interestExpenseGlId,
        BigDecimal minOpeningBalance,
        BigDecimal minOperatingBalance,
        BigDecimal maxBalance,
        BigDecimal interestRate,
        String interestCalcMethod,
        String interestPostingFrequency,
        String dayCount,
        int dormancyDays,
        String requiredKycTier,
        boolean allowOverdraft,
        BigDecimal maxOverdraftLimit,
        BigDecimal maxWithdrawalAmount,
        BigDecimal dailyWithdrawalLimit,
        Instant createdAt,
        Instant publishedAt) {
}
