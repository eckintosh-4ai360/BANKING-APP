package com.company.banking.common.error;

/**
 * Base for every expected business failure. Mapped to an HTTP response by {@link GlobalExceptionHandler}.
 * Messages must be safe to show to API clients: no secrets, no internal identifiers of other tenants.
 */
public class BankingException extends RuntimeException {

    private final transient ErrorCode errorCode;

    public BankingException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage());
    }

    public BankingException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
