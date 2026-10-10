package com.company.banking.channel.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ChannelErrorCode implements ErrorCode {

    CHANNEL_NOT_ENABLED(HttpStatus.FORBIDDEN, "Mobile banking is not available at this institution."),
    DEVICE_ID_REQUIRED(HttpStatus.BAD_REQUEST, "The app must identify its installation (X-Device-Id header)."),
    INVALID_PHONE_NUMBER(HttpStatus.BAD_REQUEST, "Enter the phone number registered with your institution."),
    CODE_INVALID(HttpStatus.UNPROCESSABLE_CONTENT,
            "The code is wrong, used or expired. Ask for a new one if you need to."),
    TOO_MANY_CODES(HttpStatus.TOO_MANY_REQUESTS,
            "Too many codes were requested for this phone number. Try again later."),
    ALREADY_SET_UP(HttpStatus.CONFLICT, "Mobile banking is already set up for this customer."),
    WEAK_PIN(HttpStatus.UNPROCESSABLE_CONTENT,
            "Choose a PIN of 4 to 6 digits that is not one digit repeated or a run such as 1234."),
    WRONG_PIN(HttpStatus.UNPROCESSABLE_CONTENT, "The transaction PIN is wrong."),
    PIN_LOCKED(HttpStatus.LOCKED,
            "Your transaction PIN is locked after too many wrong attempts. Reset it with a code sent to your phone.");

    private final HttpStatus status;
    private final String defaultMessage;

    ChannelErrorCode(HttpStatus status, String defaultMessage) {
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
