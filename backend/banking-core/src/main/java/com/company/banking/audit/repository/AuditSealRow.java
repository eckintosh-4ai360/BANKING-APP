package com.company.banking.audit.repository;

import java.time.Instant;
import java.util.UUID;

/**
 * One seal of an audit trail: {@code rowCount} rows in {@code [rangeStart, rangeEnd)} with their Merkle root,
 * chained to the previous seal and signed.
 *
 * @param tenantId the institution, or {@code null} for the platform's own trail
 */
public record AuditSealRow(
        UUID id,
        UUID tenantId,
        long sequenceNo,
        Instant rangeStart,
        Instant rangeEnd,
        int rowCount,
        String merkleRoot,
        String previousHash,
        String sealHash,
        int keyVersion,
        String signature,
        Instant createdAt) {
}
