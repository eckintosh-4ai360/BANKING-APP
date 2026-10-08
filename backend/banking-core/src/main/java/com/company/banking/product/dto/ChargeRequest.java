package com.company.banking.product.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * A charge on one kind of money movement. FLAT needs {@code flatAmount}; PERCENT needs {@code rate} (percent of the
 * amount moved) and may be kept within {@code minAmount} and {@code maxAmount}. Amounts are decimal strings in the
 * product currency.
 */
public record ChargeRequest(
        @NotBlank @Pattern(regexp = "^(CASH_DEPOSIT|CASH_WITHDRAWAL|TRANSFER_OUT)$") String event,
        @NotBlank @Size(max = 80) String name,
        @NotBlank @Pattern(regexp = "^(FLAT|PERCENT)$") String calculation,
        @DecimalMin(value = "0", inclusive = false) BigDecimal flatAmount,
        @DecimalMin(value = "0", inclusive = false) @DecimalMax("100") BigDecimal rate,
        @DecimalMin("0") BigDecimal minAmount,
        @DecimalMin(value = "0", inclusive = false) BigDecimal maxAmount) {
}
