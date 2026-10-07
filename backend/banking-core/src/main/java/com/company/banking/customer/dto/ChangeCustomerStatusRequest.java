package com.company.banking.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangeCustomerStatusRequest(
        @NotBlank @Pattern(regexp = "^(ACTIVE|RESTRICTED|FROZEN|CLOSED)$") String status,
        @NotBlank @Size(max = 255) String reason,
        @NotNull Long version) {
}
