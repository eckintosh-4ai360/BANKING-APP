package com.company.banking.teller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * A supervisor's decision (accepting a balancing difference, approving or rejecting a cash movement).
 */
public record SupervisorDecisionRequest(@NotBlank @Size(max = 300) String note, @NotNull Long version) {
}
