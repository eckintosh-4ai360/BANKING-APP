package com.company.banking.operations.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum OperationsErrorCode implements ErrorCode {

    HOLIDAY_NOT_IN_FUTURE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Holidays can only be added or removed for days after the current business date."),
    HOLIDAY_EXISTS(HttpStatus.CONFLICT, "This day is already a holiday."),
    NO_BUSINESS_DAY_AHEAD(HttpStatus.UNPROCESSABLE_CONTENT,
            "The calendar has no working day in the next year. Check the working week and holidays."),
    EOD_ALREADY_RUNNING(HttpStatus.CONFLICT, "End-of-day is already running for this institution."),
    EOD_CHECKS_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "The business date cannot close yet."),
    EOD_STEP_FAILED(HttpStatus.UNPROCESSABLE_CONTENT, "An end-of-day step failed. Fix the cause and resume the run."),
    EOD_NOTHING_TO_RESUME(HttpStatus.UNPROCESSABLE_CONTENT, "There is no failed end-of-day run to resume.");

    private final HttpStatus status;
    private final String defaultMessage;

    OperationsErrorCode(HttpStatus status, String defaultMessage) {
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
