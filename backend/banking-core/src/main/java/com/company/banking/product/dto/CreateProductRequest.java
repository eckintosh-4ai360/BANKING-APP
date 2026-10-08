package com.company.banking.product.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A new product with the terms of its first (draft) version.
 */
public record CreateProductRequest(
        @NotBlank @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{1,29}$",
                message = "must be 2-30 upper-case letters, digits, hyphens or underscores") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "^(SAVINGS|CURRENT|SUSU|FIXED_DEPOSIT|TARGET_SAVINGS)$") String productType,
        @Size(max = 500) String description,
        @NotNull @Valid ProductTermsRequest terms) {
}
