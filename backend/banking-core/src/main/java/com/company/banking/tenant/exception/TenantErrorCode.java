package com.company.banking.tenant.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TenantErrorCode implements ErrorCode {

    FEATURE_NOT_LICENSED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This feature is not licensed for the institution. Contact the platform provider."),
    UNKNOWN_FEATURE(HttpStatus.UNPROCESSABLE_CONTENT, "The feature code is not recognised."),
    TENANT_CODE_TAKEN(HttpStatus.CONFLICT, "An institution with this code already exists.");

    private final HttpStatus status;
    private final String defaultMessage;

    TenantErrorCode(HttpStatus status, String defaultMessage) {
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
