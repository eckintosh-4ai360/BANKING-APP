package com.company.banking.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Asks for a transaction to be reversed. Reversal always needs a second person's approval.
 */
public record ReverseTransactionRequest(@NotBlank @Size(max = 250) String reason) {
}
