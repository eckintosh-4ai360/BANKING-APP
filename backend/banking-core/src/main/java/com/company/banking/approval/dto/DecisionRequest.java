package com.company.banking.approval.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * @param note required when rejecting
 */
public record DecisionRequest(
        @Size(max = 300) String note,
        @NotNull Long version) {
}
