package com.company.banking.approval.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

/**
 * @param payload          what the maker asked for, as submitted
 * @param resultResourceId what the approved action created (e.g. the posted transaction or journal)
 */
public record ApprovalResponse(
        UUID id,
        String requestType,
        String status,
        UUID branchId,
        BigDecimal amount,
        String currency,
        String resourceType,
        UUID resourceId,
        String summary,
        JsonNode payload,
        UUID requestedBy,
        Instant requestedAt,
        UUID decidedBy,
        Instant decidedAt,
        String decisionNote,
        UUID resultResourceId,
        Long version) {
}
