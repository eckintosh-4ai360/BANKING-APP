package com.company.banking.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerDocumentResponse(
        UUID id,
        String documentType,
        String reviewStatus,
        String reviewNote,
        UUID reviewedBy,
        Instant reviewedAt,
        String fileName,
        String contentType,
        long sizeBytes,
        String scanStatus,
        UUID uploadedBy,
        Instant uploadedAt) {
}
