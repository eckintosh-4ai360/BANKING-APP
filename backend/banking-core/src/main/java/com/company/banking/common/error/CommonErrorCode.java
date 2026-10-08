package com.company.banking.common.error;

import org.springframework.http.HttpStatus;

public enum CommonErrorCode implements ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "The request contains invalid fields."),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "The request could not be read."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "You do not have permission to perform this action."),
    PASSWORD_CHANGE_REQUIRED(HttpStatus.FORBIDDEN, "You must change your temporary password before continuing."),
    MFA_ENROLLMENT_REQUIRED(HttpStatus.FORBIDDEN, "You must set up an authenticator app before continuing."),
    SELF_MODIFICATION_NOT_ALLOWED(HttpStatus.FORBIDDEN, "You cannot perform this action on your own access."),
    FOUR_EYES_VIOLATION(HttpStatus.FORBIDDEN,
            "A different person must check this: you cannot approve or review your own work."),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "The requested resource was not found."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "The HTTP method is not supported for this resource."),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT, "A resource with the same identifying value already exists."),
    CONCURRENT_MODIFICATION(HttpStatus.CONFLICT,
            "The resource was changed by someone else. Reload it and try again."),
    BUSINESS_RULE_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "The request violates a business rule."),
    INVALID_STATE_TRANSITION(HttpStatus.UNPROCESSABLE_CONTENT,
            "The resource cannot move to the requested state."),
    PAYLOAD_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "The request is too large."),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST,
            "This request needs an Idempotency-Key header so it can be retried safely."),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST,
            "The Idempotency-Key must be 8 to 100 letters, digits, hyphens or underscores."),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This Idempotency-Key was already used for a different request. Use a new key for a new request."),
    IDEMPOTENT_REQUEST_IN_PROGRESS(HttpStatus.CONFLICT,
            "The same request is still being processed. Wait a moment, then retry with the same key."),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please wait and try again."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");

    private final HttpStatus status;
    private final String defaultMessage;

    CommonErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    @Override
    public String code() {
        return name();
    }

    @Override
    public HttpStatus status() {
        return status;
    }

    @Override
    public String defaultMessage() {
        return defaultMessage;
    }
}
