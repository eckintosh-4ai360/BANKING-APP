package com.company.banking.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash paid out at a branch from an account. Any withdrawal charge of the product is taken on top of the amount.
 *
 * @param branchId the branch whose cash pays out; defaults to the account's branch
 */
public record CashWithdrawalRequest(
        @NotNull UUID accountId,
        UUID branchId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String narration,
        @Size(max = 60) String externalReference) {
}
