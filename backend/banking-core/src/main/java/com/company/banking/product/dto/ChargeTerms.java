package com.company.banking.product.dto;

import java.math.BigDecimal;

/**
 * A charge as configured on a product version (API responses and the transaction module).
 */
public record ChargeTerms(
        String event,
        String name,
        String calculation,
        BigDecimal flatAmount,
        BigDecimal rate,
        BigDecimal minAmount,
        BigDecimal maxAmount) {
}
