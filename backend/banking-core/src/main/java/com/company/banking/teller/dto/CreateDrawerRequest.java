package com.company.banking.teller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDrawerRequest(
        @NotNull UUID branchId,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9-]{0,19}$") String code,
        @NotBlank @Size(max = 100) String name) {
}
