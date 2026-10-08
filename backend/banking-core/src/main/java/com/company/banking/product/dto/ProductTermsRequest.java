package com.company.banking.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Terms of a product version. GL accounts default by product type when omitted (e.g. savings deposits for SAVINGS).
 * Money amounts are decimal strings in the product currency.
 *
 * @param interestRate annual percentage, e.g. {@code "5.25"}; calculated by end-of-day processing (Phase 3)
 */
public record ProductTermsRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        UUID depositGlId,
        UUID feeIncomeGlId,
        UUID interestExpenseGlId,
        @DecimalMin("0") BigDecimal minOpeningBalance,
        @DecimalMin("0") BigDecimal minOperatingBalance,
        @DecimalMin(value = "0", inclusive = false) BigDecimal maxBalance,
        @DecimalMin("0") @DecimalMax("100") BigDecimal interestRate,
        @Pattern(regexp = "^(DAILY_BALANCE|MIN_MONTHLY_BALANCE|AVG_DAILY_BALANCE)$") String interestCalcMethod,
        @Pattern(regexp = "^(NONE|MONTHLY|QUARTERLY|ANNUALLY)$") String interestPostingFrequency,
        @Pattern(regexp = "^(ACTUAL_365F|ACTUAL_360|THIRTY_360)$") String dayCount,
        @Min(30) @Max(3650) Integer dormancyDays,
        @Size(max = 20) String requiredKycTier,
        boolean allowOverdraft,
        @DecimalMin("0") BigDecimal maxOverdraftLimit,
        @DecimalMin(value = "0", inclusive = false) BigDecimal maxWithdrawalAmount,
        @DecimalMin(value = "0", inclusive = false) BigDecimal dailyWithdrawalLimit) {
}
