package com.company.banking.ledger.dto;

import java.util.UUID;

/**
 * Reverses a posted journal with an exact mirror on the current business date.
 *
 * @param financialTransactionId the reversal transaction, if the reversal belongs to one
 * @param approvedBy             approver, when the reversal went through maker-checker
 */
public record ReversalRequest(UUID journalId, String reason, UUID financialTransactionId, UUID approvedBy) {
}
