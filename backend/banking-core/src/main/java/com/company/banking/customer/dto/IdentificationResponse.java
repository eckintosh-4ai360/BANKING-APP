package com.company.banking.customer.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Never contains the full number; see the audited reveal endpoint.
 */
public record IdentificationResponse(
        UUID id,
        String idTypeCode,
        String idNumberMasked,
        String issuingCountry,
        LocalDate issueDate,
        LocalDate expiryDate,
        boolean primary,
        boolean active,
        String verificationStatus,
        Instant verifiedAt,
        String verificationReference) {
}
