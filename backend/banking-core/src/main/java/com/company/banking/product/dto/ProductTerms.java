package com.company.banking.product.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The terms an account is bound by (its product version), for the account and transaction modules.
 */
public record ProductTerms(
        UUID productId,
        String productCode,
        String productName,
        String productType,
        UUID versionId,
        int versionNo,
        String currency,
        UUID depositGlId,
        UUID feeIncomeGlId,
        UUID interestExpenseGlId,
        BigDecimal minOpeningBalance,
        BigDecimal minOperatingBalance,
        BigDecimal maxBalance,
        String requiredKycTier,
        boolean allowOverdraft,
        BigDecimal maxOverdraftLimit,
        BigDecimal maxWithdrawalAmount,
        BigDecimal dailyWithdrawalLimit,
        int dormancyDays,
        List<ChargeTerms> charges) {

    public ProductTerms {
        charges = List.copyOf(charges);
    }

    /**
     * The charge for a kind of money movement ({@code CASH_DEPOSIT}, {@code CASH_WITHDRAWAL}, {@code TRANSFER_OUT}).
     */
    public Optional<ChargeTerms> chargeFor(String event) {
        return charges.stream().filter(charge -> charge.event().equals(event)).findFirst();
    }
}
