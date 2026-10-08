package com.company.banking.approval.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ApprovalErrorCode implements ErrorCode {

    APPROVAL_NOT_PENDING(HttpStatus.UNPROCESSABLE_CONTENT, "The approval request has already been decided."),
    APPROVAL_ALREADY_PENDING(HttpStatus.CONFLICT, "An approval request for this is already waiting for a decision."),
    DECISION_NOTE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Give a reason when rejecting a request."),
    NOT_THE_REQUESTER(HttpStatus.FORBIDDEN, "Only the person who made the request can cancel it."),
    POLICY_NOT_CONFIGURABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Only withdrawals and transfers have thresholds; reversals and manual journals always need approval.");

    private final HttpStatus status;
    private final String defaultMessage;

    ApprovalErrorCode(HttpStatus status, String defaultMessage) {
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
