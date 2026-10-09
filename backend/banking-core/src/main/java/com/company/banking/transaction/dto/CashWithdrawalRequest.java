package com.company.banking.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash paid out by a teller from an account, from the drawer of the teller's open session. Any withdrawal charge
 * of the product is taken on top of the amount.
 */
public record CashWithdrawalRequest(
        @NotNull UUID accountId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String narration,
        @Size(max = 60) String externalReference) {
}
