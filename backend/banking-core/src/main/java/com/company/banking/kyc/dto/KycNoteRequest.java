package com.company.banking.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Reason for returning, rejecting or cancelling a case.
 */
public record KycNoteRequest(@NotBlank @Size(max = 500) String note, @NotNull Long version) {
}
