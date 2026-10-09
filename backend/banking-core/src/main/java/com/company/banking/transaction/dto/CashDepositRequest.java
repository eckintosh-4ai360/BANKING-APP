package com.company.banking.transaction.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Cash received by a teller and credited to an account. The cash goes into the drawer of the teller's open session.
 *
 * @param amount            a decimal string in the account currency (the server never trusts a computed total)
 * @param externalReference e.g. the deposit slip number
 */
public record CashDepositRequest(
        @NotNull UUID accountId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        @Size(max = 200) String narration,
        @Size(max = 60) String externalReference) {
}
