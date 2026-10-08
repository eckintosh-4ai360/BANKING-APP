package com.company.banking.product.service;

import com.company.banking.product.dto.ChargeTerms;
import com.company.banking.product.model.ChargeCalculation;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Computes a charge for an amount. Pure and exact: BigDecimal throughout, one rounding step (half up to the currency
 * minor unit, applied to percentage charges only), then the minimum and maximum.
 */
public final class ChargeCalculator {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private ChargeCalculator() {
    }

    /**
     * @param amount     the amount moved, positive, in the charge's currency
     * @param minorUnits decimal places of the currency (2 for GHS)
     * @return the charge, never negative, with exactly {@code minorUnits} decimal places
     */
    public static BigDecimal calculate(ChargeTerms charge, BigDecimal amount, int minorUnits) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("The amount must be positive");
        }
        BigDecimal fee = switch (ChargeCalculation.valueOf(charge.calculation())) {
            case FLAT -> charge.flatAmount();
            case PERCENT -> amount.multiply(charge.rate()).divide(HUNDRED, minorUnits, RoundingMode.HALF_UP);
        };
        if (charge.minAmount() != null && fee.compareTo(charge.minAmount()) < 0) {
            fee = charge.minAmount();
        }
        if (charge.maxAmount() != null && fee.compareTo(charge.maxAmount()) > 0) {
            fee = charge.maxAmount();
        }
        // Configured amounts were validated against the currency precision, so this never rounds.
        return fee.setScale(minorUnits, RoundingMode.UNNECESSARY);
    }
}
