package com.company.banking.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Envelope for every error response. {@code code} is machine-readable and stable; {@code message} is for humans.
 * Rejected values are deliberately never echoed back because they may contain secrets (passwords, PINs).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiErrorResponse(
        boolean success,
        String code,
        String message,
        Instant timestamp,
        String traceId,
        List<FieldViolation> errors) {

    public static ApiErrorResponse of(String code, String message, String traceId) {
        return new ApiErrorResponse(false, code, message, Instant.now(), traceId, List.of());
    }

    public static ApiErrorResponse of(String code, String message, String traceId, List<FieldViolation> errors) {
        return new ApiErrorResponse(false, code, message, Instant.now(), traceId, List.copyOf(errors));
    }

    public record FieldViolation(String field, String message) {
    }
}
