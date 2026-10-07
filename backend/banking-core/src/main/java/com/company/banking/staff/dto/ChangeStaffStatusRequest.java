package com.company.banking.staff.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeStaffStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|SUSPENDED|TERMINATED)$") String status,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long version) {
}
