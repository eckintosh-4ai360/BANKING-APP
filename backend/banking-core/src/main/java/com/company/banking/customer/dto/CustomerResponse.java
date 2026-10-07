package com.company.banking.customer.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        String customerNumber,
        String customerType,
        String status,
        String statusReason,
        String kycStatus,
        String kycTierCode,
        Instant kycVerifiedAt,
        String riskLevel,
        String displayName,
        String primaryPhone,
        String email,
        String preferredLanguage,
        UUID homeBranchId,
        UUID relationshipOfficerId,
        String onboardingChannel,
        IndividualProfileResponse individual,
        BusinessProfileResponse business,
        List<AddressResponse> addresses,
        List<IdentificationResponse> identifications,
        List<NextOfKinResponse> nextOfKin,
        List<RelatedPartyResponse> relatedParties,
        List<CustomerDocumentResponse> documents,
        Instant createdAt,
        Instant updatedAt,
        Long version) {
}
