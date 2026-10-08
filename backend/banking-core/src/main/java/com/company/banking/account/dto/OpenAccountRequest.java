package com.company.banking.account.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/**
 * Opens an account for {@code customerId} (the primary holder) under the product's published terms.
 *
 * @param branchId     the servicing branch; defaults to the customer's home branch
 * @param title        defaults to the primary holder's name
 * @param otherHolders joint holders (JOINT_ANY, JOINT_ALL) or signatories (BUSINESS); empty for SINGLE
 */
public record OpenAccountRequest(
        @NotNull UUID customerId,
        @NotNull UUID productId,
        UUID branchId,
        @NotBlank @Pattern(regexp = "^(SINGLE|JOINT_ANY|JOINT_ALL|BUSINESS)$") String ownershipType,
        @Size(max = 150) String title,
        @Size(max = 9) List<@Valid @NotNull HolderRequest> otherHolders) {

    public record HolderRequest(
            @NotNull UUID customerId,
            @NotBlank @Pattern(regexp = "^(JOINT|SIGNATORY)$") String role) {
    }
}
