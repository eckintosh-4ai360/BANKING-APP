package com.company.banking.branch.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeBranchStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE|CLOSED)$") String status,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long version) {
}
