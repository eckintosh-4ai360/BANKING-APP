package com.company.banking.product.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ProductErrorCode implements ErrorCode {

    PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "The product is not available for new accounts (inactive or without published terms)."),
    VERSION_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Published terms cannot change. Create a new version instead."),
    DRAFT_ALREADY_EXISTS(HttpStatus.CONFLICT, "The product already has a draft version."),
    INVALID_GL_MAPPING(HttpStatus.UNPROCESSABLE_CONTENT,
            "The GL account does not fit: deposits post to a liability account, fees to income, interest to expense."),
    INVALID_TERMS(HttpStatus.UNPROCESSABLE_CONTENT, "The product terms are not consistent.");

    private final HttpStatus status;
    private final String defaultMessage;

    ProductErrorCode(HttpStatus status, String defaultMessage) {
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
