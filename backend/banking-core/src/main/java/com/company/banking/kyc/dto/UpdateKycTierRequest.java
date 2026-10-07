package com.company.banking.kyc.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateKycTierRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 255) String description,
        boolean requiresIdentification,
        boolean requiresIdDocument,
        boolean requiresSelfie,
        boolean requiresAddress,
        boolean requiresProofOfAddress,
        boolean requiresIdentityVerification,
        boolean requiresNextOfKin,
        boolean requiresSignature,
        boolean requiresEmploymentInfo,
        boolean active,
        @NotNull Long version) {
}
