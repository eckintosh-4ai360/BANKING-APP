package com.company.banking.susu.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SusuErrorCode implements ErrorCode {

    FREQUENCY_EXISTS(HttpStatus.CONFLICT, "A susu frequency with this code already exists."),
    FREQUENCY_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The susu frequency does not exist or is inactive."),
    NOT_A_SUSU_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Susu plans run on an open susu account the customer holds."),
    PLAN_ALREADY_RUNNING(HttpStatus.CONFLICT, "The account already has a running susu plan."),
    INVALID_PLAN(HttpStatus.UNPROCESSABLE_CONTENT,
            "Commission must be fewer contributions than a cycle, and dates must not be in the past."),
    PLAN_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "The susu plan is not running."),
    PLAN_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "The susu plan is not for this customer and account."),
    AMOUNT_NOT_WHOLE_CONTRIBUTIONS(HttpStatus.UNPROCESSABLE_CONTENT,
            "Susu payments must be a whole number of contributions."),
    OVERPAID(HttpStatus.UNPROCESSABLE_CONTENT, "The payment covers more contributions than are scheduled."),
    CONTRIBUTION_NOT_UNPAID(HttpStatus.UNPROCESSABLE_CONTENT, "Only an expected or missed contribution can be waived.");

    private final HttpStatus status;
    private final String defaultMessage;

    SusuErrorCode(HttpStatus status, String defaultMessage) {
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
