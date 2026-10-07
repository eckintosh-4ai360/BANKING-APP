package com.company.banking.iam.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum IamErrorCode implements ErrorCode {

    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "The institution, username or password is incorrect."),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "The session has expired. Please sign in again."),
    SESSION_REVOKED(HttpStatus.UNAUTHORIZED, "The session has ended. Please sign in again."),
    ACCOUNT_LOCKED(HttpStatus.LOCKED,
            "The account is temporarily locked after too many failed attempts. Try again later or contact an "
                    + "administrator."),
    ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "The account is disabled. Contact an administrator."),
    PASSWORD_POLICY_VIOLATION(HttpStatus.UNPROCESSABLE_CONTENT, "The new password does not meet the password policy."),
    INVALID_PERMISSION(HttpStatus.UNPROCESSABLE_CONTENT, "One or more permissions are unknown or not assignable."),
    ROLE_INACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Inactive roles cannot be assigned."),
    INVALID_MFA_CHALLENGE(HttpStatus.UNAUTHORIZED, "The sign-in attempt has expired. Please sign in again."),
    INVALID_MFA_CODE(HttpStatus.UNAUTHORIZED, "The authentication code is incorrect or has already been used."),
    MFA_ALREADY_ENABLED(HttpStatus.CONFLICT, "An authenticator is already set up for this account."),
    MFA_NOT_STARTED(HttpStatus.UNPROCESSABLE_CONTENT, "Start the authenticator setup before activating it.");

    private final HttpStatus status;
    private final String defaultMessage;

    IamErrorCode(HttpStatus status, String defaultMessage) {
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
