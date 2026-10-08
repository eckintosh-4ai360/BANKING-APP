package com.company.banking.product.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProductRequest(
        @NotBlank @Size(max = 120) String name,
        @Size(max = 500) String description,
        @NotBlank @Pattern(regexp = "^(ACTIVE|INACTIVE)$") String status,
        @NotNull Long version) {
}
