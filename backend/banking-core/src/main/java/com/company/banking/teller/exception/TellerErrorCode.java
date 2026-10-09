package com.company.banking.teller.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TellerErrorCode implements ErrorCode {

    VAULT_EXISTS(HttpStatus.CONFLICT, "The branch already has a vault for this currency."),
    NO_VAULT(HttpStatus.UNPROCESSABLE_CONTENT, "The branch has no vault for this currency."),
    DRAWER_EXISTS(HttpStatus.CONFLICT, "The branch already has a drawer with this code."),
    DRAWER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The drawer is not active."),
    DRAWER_IN_USE(HttpStatus.CONFLICT, "Another teller is working this drawer."),
    SESSION_ALREADY_OPEN(HttpStatus.CONFLICT, "You already have a teller session open."),
    TELLER_SESSION_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Open a teller session on a drawer before taking or paying out cash."),
    SESSION_NOT_OPEN(HttpStatus.UNPROCESSABLE_CONTENT, "The teller session is not open."),
    SESSION_NOT_BALANCING(HttpStatus.UNPROCESSABLE_CONTENT, "The teller session has no difference to accept."),
    NOT_YOUR_SESSION(HttpStatus.FORBIDDEN, "Only the teller of the session can count and close it."),
    OPENING_COUNT_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT,
            "The cash counted does not match what the drawer should hold. Ask a supervisor to check the drawer."),
    INVALID_DENOMINATIONS(HttpStatus.UNPROCESSABLE_CONTENT,
            "Denominations must be amounts of the currency, each counted as a whole number of pieces."),
    CASH_INSUFFICIENT(HttpStatus.UNPROCESSABLE_CONTENT, "There is not enough cash for this."),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "The drawer holds a different currency."),
    MOVEMENT_NOT_PENDING(HttpStatus.UNPROCESSABLE_CONTENT, "The cash movement is no longer waiting for this step."),
    INVALID_MOVEMENT(HttpStatus.UNPROCESSABLE_CONTENT, "The cash movement does not fit its type.");

    private final HttpStatus status;
    private final String defaultMessage;

    TellerErrorCode(HttpStatus status, String defaultMessage) {
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
