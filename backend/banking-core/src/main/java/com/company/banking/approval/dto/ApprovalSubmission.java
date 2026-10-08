package com.company.banking.approval.dto;

import com.company.banking.approval.model.ApprovalType;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * What another module submits for approval. The payload is everything the action needs to run later; it is stored
 * as JSON and handed back to the module's handler unchanged.
 *
 * @param branchId   branch the action belongs to; only checkers whose scope covers it can decide
 * @param resourceId the existing record the action is about (e.g. the transaction to reverse), if any
 */
public record ApprovalSubmission(
        ApprovalType type,
        UUID branchId,
        BigDecimal amount,
        String currency,
        String resourceType,
        UUID resourceId,
        String summary,
        Object payload) {
}
