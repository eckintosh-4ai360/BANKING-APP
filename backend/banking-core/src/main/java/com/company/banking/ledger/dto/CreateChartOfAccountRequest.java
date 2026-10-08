package com.company.banking.ledger.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A new GL account. The normal side follows the class unless set (contra accounts, e.g. a provision against loans,
 * are assets kept on the credit side).
 */
public record CreateChartOfAccountRequest(
        @NotBlank @Pattern(regexp = "^[0-9A-Z][0-9A-Z.-]{0,19}$",
                message = "must be 1-20 upper-case letters, digits, dots or hyphens") String code,
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Pattern(regexp = "^(ASSET|LIABILITY|EQUITY|INCOME|EXPENSE)$") String accountClass,
        @Pattern(regexp = "^(DEBIT|CREDIT)$") String normalSide,
        @NotNull UUID parentId,
        boolean header,
        boolean manualPostingAllowed) {
}
