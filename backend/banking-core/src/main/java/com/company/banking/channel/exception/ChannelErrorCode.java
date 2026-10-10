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
            "Your transaction PIN is locked after too many wrong attempts. Reset it with a code sent to your phone."),
    TRANSFER_LIMIT(HttpStatus.UNPROCESSABLE_CONTENT, "The transfer is above your limit for the app."),
    DESTINATION_NOT_FOUND(HttpStatus.UNPROCESSABLE_CONTENT,
            "No account of this institution with that number can receive money."),
    BENEFICIARY_TYPE_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Only accounts of this institution can be saved for now; bank and mobile money transfers come later."),
    OWN_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "This is one of your own accounts; pay it directly."),
    BENEFICIARY_EXISTS(HttpStatus.CONFLICT, "This account is already one of your beneficiaries."),
    SIGN_UP_NOT_AVAILABLE(HttpStatus.FORBIDDEN,
            "Signing up in the app is not available at this institution. Visit a branch to become a customer."),
    TOO_YOUNG(HttpStatus.UNPROCESSABLE_CONTENT,
            "You are not old enough to sign up in the app. Visit a branch with a parent or guardian."),
    NOT_SIGNING_UP(HttpStatus.CONFLICT, "You are already a customer; there is nothing to sign up for."),
    SIGN_UP_INCOMPLETE(HttpStatus.UNPROCESSABLE_CONTENT, "Some steps of signing up are not finished yet."),
    NOT_VERIFIED_YET(HttpStatus.UNPROCESSABLE_CONTENT,
            "Your details have not been approved yet. We will let you know as soon as they are.");

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
