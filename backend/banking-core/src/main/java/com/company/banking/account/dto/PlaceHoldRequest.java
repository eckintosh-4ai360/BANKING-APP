package com.company.banking.account.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Reserves funds on an account. PENDING_PAYMENT and LOAN_COLLATERAL holds are placed by the payment and loan modules
 * only.
 *
 * @param amount    a decimal string in the account currency
 * @param expiresAt when the hold lapses on its own; open-ended when null
 */
public record PlaceHoldRequest(
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @NotBlank @Pattern(regexp = "^(LIEN|LEGAL|FRAUD_REVIEW)$") String holdType,
        @NotBlank @Size(max = 300) String reason,
        @Size(max = 60) String reference,
        @Future Instant expiresAt) {
}
