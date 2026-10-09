package com.company.banking.fieldops.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FieldErrorCode implements ErrorCode {

    OFFICER_EXISTS(HttpStatus.CONFLICT, "This staff member is already a field officer."),
    STAFF_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Only an active staff member can be a field officer."),
    NOT_A_FIELD_OFFICER(HttpStatus.FORBIDDEN, "You are not registered as a field officer."),
    OFFICER_SUSPENDED(HttpStatus.UNPROCESSABLE_CONTENT, "The field officer is suspended."),
    DEVICE_IN_USE(HttpStatus.CONFLICT, "This device is registered to another field officer."),
    DEVICE_NOT_REGISTERED(HttpStatus.FORBIDDEN,
            "This device is not registered to you, or its registration was revoked."),
    DEVICE_REVOKED(HttpStatus.UNPROCESSABLE_CONTENT, "The device registration is already revoked."),
    CUSTOMER_NOT_ASSIGNED(HttpStatus.UNPROCESSABLE_CONTENT, "The customer is not assigned to you."),
    ACCOUNT_NOT_FOR_CUSTOMER(HttpStatus.UNPROCESSABLE_CONTENT, "The account does not belong to the customer."),
    INVALID_COLLECTION_TIME(HttpStatus.UNPROCESSABLE_CONTENT, "The collection is dated in the future."),
    ASSIGNMENT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "The assignment has already ended."),
    ALREADY_ASSIGNED(HttpStatus.CONFLICT, "The customer is already assigned to this officer."),
    CUSTOMER_OTHER_BRANCH(HttpStatus.UNPROCESSABLE_CONTENT,
            "The customer belongs to another branch than the field officer."),
    ALERT_NOT_OPEN(HttpStatus.UNPROCESSABLE_CONTENT, "The alert is already resolved."),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "The drawer holds a different currency than the officer."),
    REMITTANCE_ABOVE_CASH(HttpStatus.UNPROCESSABLE_CONTENT, "The officer does not carry that much cash.");

    private final HttpStatus status;
    private final String defaultMessage;

    FieldErrorCode(HttpStatus status, String defaultMessage) {
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
