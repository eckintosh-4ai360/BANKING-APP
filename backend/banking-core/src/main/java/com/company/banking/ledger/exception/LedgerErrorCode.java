package com.company.banking.ledger.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LedgerErrorCode implements ErrorCode {

    UNBALANCED_JOURNAL(HttpStatus.UNPROCESSABLE_CONTENT, "Debits and credits must be equal in every currency."),
    INVALID_POSTING(HttpStatus.UNPROCESSABLE_CONTENT, "The posting is not valid."),
    INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Amounts must be positive and use no more decimal places than the currency allows."),
    CURRENCY_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT, "The currency is not supported."),
    GL_ACCOUNT_NOT_POSTABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "The GL account does not accept postings (it is a header or inactive account)."),
    GL_ACCOUNT_NOT_MANUAL(HttpStatus.UNPROCESSABLE_CONTENT,
            "Manual journals can only post to GL accounts that allow manual posting."),
    SYSTEM_ACCOUNT_MISSING(HttpStatus.UNPROCESSABLE_CONTENT,
            "A required system GL account is missing from the chart of accounts."),
    LEDGER_ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "The ledger account is closed."),
    INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "The account does not have enough available funds."),
    PERIOD_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "The accounting period for this date is closed."),
    PERIOD_CLOSE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This period cannot be closed yet: it must have ended and all earlier periods must be closed."),
    JOURNAL_ALREADY_REVERSED(HttpStatus.CONFLICT, "This journal has already been reversed."),
    REVERSAL_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT,
            "A reversal cannot itself be reversed. Post a new journal instead."),
    APPROVAL_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT, "Manual journals must be approved by a second person."),
    GL_ACCOUNT_IN_USE(HttpStatus.UNPROCESSABLE_CONTENT,
            "The GL account still has a balance, active sub-accounts or active child accounts."),
    SYSTEM_GL_ACCOUNT_PROTECTED(HttpStatus.UNPROCESSABLE_CONTENT,
            "System GL accounts are used by automatic postings and cannot be deactivated."),
    INVALID_PARENT_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT,
            "The parent must be an active header account of the same class."),
    BRANCH_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "The branch is not active.");

    private final HttpStatus status;
    private final String defaultMessage;

    LedgerErrorCode(HttpStatus status, String defaultMessage) {
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
