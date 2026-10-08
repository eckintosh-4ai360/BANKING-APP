package com.company.banking.ledger.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record JournalSummary(
        UUID id,
        String journalNumber,
        LocalDate businessDate,
        Instant postedAt,
        String sourceType,
        String sourceReference,
        UUID branchId,
        String description,
        UUID reversesJournalId) {
}
