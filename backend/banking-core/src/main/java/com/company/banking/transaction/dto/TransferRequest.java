package com.company.banking.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Moves money between two accounts of the institution in the same currency. Any transfer charge of the source
 * account's product is taken from the source on top of the amount.
 */
public record TransferRequest(
        @NotNull UUID fromAccountId,
        @NotNull UUID toAccountId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String narration,
        @Size(max = 60) String externalReference) {
}
