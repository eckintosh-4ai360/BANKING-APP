package com.company.banking.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash received at a branch and credited to an account.
 *
 * @param branchId          the branch whose cash the money goes into; defaults to the account's branch
 * @param amount            a decimal string in the account currency (the server never trusts a computed total)
 * @param externalReference e.g. the deposit slip number
 */
public record CashDepositRequest(
        @NotNull UUID accountId,
        UUID branchId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String narration,
        @Size(max = 60) String externalReference) {
}
