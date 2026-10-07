package com.company.banking.kyc.dto;

public record KycTierResponse(
        String code,
        String name,
        String description,
        int tierRank,
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
        Long version) {
}
