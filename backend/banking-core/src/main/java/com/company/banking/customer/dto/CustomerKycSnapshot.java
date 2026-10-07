package com.company.banking.customer.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the KYC module needs to evaluate tier requirements, without exposing customer entities.
 *
 * @param documentReviewStatuses review statuses of documents, by document type
 * @param hasRelatedParties      a business has at least one active director, owner or signatory
 */
public record CustomerKycSnapshot(
        UUID customerId,
        String customerNumber,
        String customerType,
        String status,
        String kycStatus,
        String kycTierCode,
        UUID homeBranchId,
        String displayName,
        boolean hasActiveIdentification,
        PrimaryIdentification primaryIdentification,
        boolean hasActiveAddress,
        Map<String, List<String>> documentReviewStatuses,
        boolean hasNextOfKin,
        boolean hasEmploymentInfo,
        boolean hasRelatedParties) {

    public record PrimaryIdentification(UUID id, String idTypeCode, boolean supportsElectronicVerification,
                                        String verificationStatus, boolean expired) {
    }
}
