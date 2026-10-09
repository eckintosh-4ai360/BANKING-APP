package com.company.banking.audit.dto;

import com.company.banking.audit.repository.AuditSealRow;
import java.time.Instant;
import java.util.UUID;

public record AuditSealResponse(
        UUID id,
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

    public static AuditSealResponse from(AuditSealRow row) {
        return new AuditSealResponse(row.id(), row.sequenceNo(), row.rangeStart(), row.rangeEnd(), row.rowCount(),
                row.merkleRoot(), row.previousHash(), row.sealHash(), row.keyVersion(), row.signature(),
                row.createdAt());
    }
}
