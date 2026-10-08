package com.company.banking.ledger.dto;

import com.company.banking.ledger.model.JournalSource;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A journal to post. The business date is always the institution's current business date; {@code valueDate}
 * (defaulting to it) may only lie in the past.
 *
 * @param sourceReference        human reference of what produced the journal (e.g. a transaction reference)
 * @param financialTransactionId the transaction this journal belongs to, if any
 * @param originBranchId         branch where the event happened
 * @param approvedBy             second person who approved a manual journal (required for {@code MANUAL})
 */
public record PostingRequest(
        JournalSource source,
        String sourceReference,
        UUID financialTransactionId,
        UUID originBranchId,
        LocalDate valueDate,
        String description,
        UUID approvedBy,
        List<PostingLine> lines) {
}
