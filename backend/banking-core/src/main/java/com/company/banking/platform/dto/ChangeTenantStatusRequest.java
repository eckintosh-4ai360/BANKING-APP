package com.company.banking.platform.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeTenantStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|SUSPENDED|TERMINATED)$") String status,
        @NotBlank @Size(max = 255) String reason) {
}
