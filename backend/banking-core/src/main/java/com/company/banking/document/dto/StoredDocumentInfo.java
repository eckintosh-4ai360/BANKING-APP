package com.company.banking.document.dto;

import java.time.Instant;
import java.util.UUID;

public record StoredDocumentInfo(
        UUID id,
        String fileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String scanStatus,
        UUID uploadedBy,
        Instant uploadedAt) {
}
