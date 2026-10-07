package com.company.banking.customer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A note is required when rejecting.
 */
public record DocumentReviewRequest(
        @NotBlank @Pattern(regexp = "^(ACCEPTED|REJECTED)$") String decision,
        @Size(max = 255) String note) {
}
