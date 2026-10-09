package com.company.banking.teller.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Asks to move cash. Which fields apply depends on the type:
 * <ul>
 *   <li>{@code VAULT_TO_DRAWER}, {@code DRAWER_TO_VAULT}: {@code drawerId} (the vault is the drawer's branch vault);</li>
 *   <li>{@code BANK_TO_VAULT}, {@code VAULT_TO_BANK}: {@code branchId} of the vault;</li>
 *   <li>{@code VAULT_TO_VAULT}: {@code branchId} (sending) and {@code toBranchId} (receiving).</li>
 * </ul>
 */
public record CashMovementRequest(
        @NotBlank @Pattern(regexp = "^(VAULT_TO_DRAWER|DRAWER_TO_VAULT|VAULT_TO_VAULT|BANK_TO_VAULT|VAULT_TO_BANK)$")
        String movementType,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal amount,
        UUID branchId,
        UUID drawerId,
        UUID toBranchId,
        @Size(max = 300) String note) {
}
