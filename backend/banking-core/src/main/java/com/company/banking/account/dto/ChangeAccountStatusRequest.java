package com.company.banking.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Staff status changes: restrict (no debits), freeze (nothing moves) or (re)activate. Dormancy is set by the
 * dormancy job and closing has its own endpoint.
 */
public record ChangeAccountStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|RESTRICTED|FROZEN)$") String status,
        @NotBlank @Size(max = 300) String reason,
        @NotNull Long version) {
}
