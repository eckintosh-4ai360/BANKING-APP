package com.company.banking.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * @param initiatedBy       who asked for the movement (the maker when it went through approval)
 * @param approvedBy        the checker, when the movement needed approval
 * @param reversedBy        the checker who approved the reversal
 */
public record TransactionResponse(
        UUID id,
        String reference,
        String transactionType,
        String status,
        String channel,
        String currency,
        BigDecimal amount,
        BigDecimal feeAmount,
        UUID debitAccountId,
        String debitAccountNumber,
        UUID creditAccountId,
        String creditAccountNumber,
        UUID branchId,
        UUID journalEntryId,
        LocalDate businessDate,
        LocalDate valueDate,
        String narration,
        String externalReference,
        UUID initiatedBy,
        UUID approvedBy,
        UUID approvalRequestId,
        Instant createdAt,
        Instant reversedAt,
        UUID reversedBy,
        UUID reversalJournalEntryId,
        String reversalReason) {
}
