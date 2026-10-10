package com.company.banking.notification.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NotificationErrorCode implements ErrorCode {

    SMS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Text messages cannot be sent at the moment. Try again later.");

    private final HttpStatus status;
    private final String defaultMessage;

    NotificationErrorCode(HttpStatus status, String defaultMessage) {
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
