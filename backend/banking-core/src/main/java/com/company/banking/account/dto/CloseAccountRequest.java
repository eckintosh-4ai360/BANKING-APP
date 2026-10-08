package com.company.banking.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CloseAccountRequest(
        @NotBlank @Size(max = 300) String reason,
        @NotNull Long version) {
}
