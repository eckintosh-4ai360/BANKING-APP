package com.company.banking.operations.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record EodRunResponse(
        UUID id,
        LocalDate businessDate,
        LocalDate nextBusinessDate,
        String status,
        Instant startedAt,
        UUID startedBy,
        Instant finishedAt,
        String failedStep,
        String failureMessage,
        int attempts,
        List<Step> steps) {

    public record Step(String code, int order, String status, JsonNode result, int attempts, Instant startedAt,
                       Instant finishedAt, String error) {
    }
}
