package com.company.banking.transaction.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

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
        Instant createdAt,
        Instant reversedAt,
        String reversalReason) {
}
