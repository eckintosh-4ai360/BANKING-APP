package com.company.banking.approval.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * @param thresholdAmount movements at or above this amount wait for a checker
 */
public record ApprovalPolicyRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal thresholdAmount,
        boolean active) {
}
