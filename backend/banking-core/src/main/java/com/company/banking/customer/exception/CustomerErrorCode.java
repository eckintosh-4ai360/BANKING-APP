package com.company.banking.customer.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum CustomerErrorCode implements ErrorCode {

    DUPLICATE_IDENTIFICATION(HttpStatus.CONFLICT, "This identity document is already registered to another customer."),
    DUPLICATE_BUSINESS_REGISTRATION(HttpStatus.CONFLICT,
            "A customer with this business registration number already exists."),
    CUSTOMER_PROFILE_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT,
            "The profile details do not match the customer type."),
    IDENTIFICATION_TYPE_NOT_ACCEPTED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This identification type is not accepted for this customer."),
    INVALID_IDENTIFICATION_NUMBER(HttpStatus.UNPROCESSABLE_CONTENT,
            "The identification number does not match the required format."),
    IDENTIFICATION_EXPIRY_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This identification type requires an expiry date."),
    IDENTIFICATION_EXPIRED(HttpStatus.UNPROCESSABLE_CONTENT, "The identity document has expired."),
    KYC_UNDER_REVIEW(HttpStatus.UNPROCESSABLE_CONTENT,
            "Customer data cannot change while KYC is under review. Ask the reviewer to return the case."),
    KYC_DATA_LOCKED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Verified identity data can only change through a KYC update case."),
    INVALID_IDENTIFICATION_TYPE_FORMAT(HttpStatus.UNPROCESSABLE_CONTENT,
            "The format pattern is not a valid regular expression."),
    CUSTOMER_HAS_OPEN_HOLDINGS(HttpStatus.UNPROCESSABLE_CONTENT,
            "The customer still holds open accounts. Close them before closing the customer.");

    private final HttpStatus status;
    private final String defaultMessage;

    CustomerErrorCode(HttpStatus status, String defaultMessage) {
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
