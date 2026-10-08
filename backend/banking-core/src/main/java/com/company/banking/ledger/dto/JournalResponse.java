package com.company.banking.ledger.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * @param reversedByJournalId the reversal of this journal, if it has been reversed
 */
public record JournalResponse(
        UUID id,
        String journalNumber,
        LocalDate businessDate,
        LocalDate valueDate,
        Instant postedAt,
        String sourceType,
        String sourceReference,
        UUID financialTransactionId,
        UUID reversesJournalId,
        UUID reversedByJournalId,
        UUID branchId,
        UUID postedBy,
        UUID approvedBy,
        String description,
        List<JournalLineResponse> lines) {
}
