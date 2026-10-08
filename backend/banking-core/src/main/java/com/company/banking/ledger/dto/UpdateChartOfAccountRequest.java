package com.company.banking.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateChartOfAccountRequest(
        @NotBlank @Size(max = 120) String name,
        boolean manualPostingAllowed,
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status,
        @NotNull Long version) {
}
