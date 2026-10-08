package com.company.banking.account.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AccountErrorCode implements ErrorCode {

    CUSTOMER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Every holder must be an active customer who has completed KYC."),
    KYC_TIER_TOO_LOW(HttpStatus.UNPROCESSABLE_CONTENT,
            "A holder's KYC tier is below the tier this product requires."),
    INVALID_OWNERSHIP(HttpStatus.UNPROCESSABLE_CONTENT,
            "The holders do not fit the ownership type: joint accounts need individual joint holders, business "
                    + "accounts belong to a business customer and may add signatories, single accounts have one "
                    + "holder."),
    BRANCH_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Accounts can only be opened at an active branch."),
    OPENING_BALANCE_NOT_MET(HttpStatus.UNPROCESSABLE_CONTENT,
            "The account is still waiting for its minimum opening deposit."),
    ACCOUNT_NOT_EMPTY(HttpStatus.UNPROCESSABLE_CONTENT,
            "Only an account with a zero balance and no active holds can be closed."),
    ACCOUNT_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT, "The account is closed."),
    INSUFFICIENT_FUNDS_FOR_HOLD(HttpStatus.UNPROCESSABLE_CONTENT,
            "The hold is larger than the available balance of the account.");

    private final HttpStatus status;
    private final String defaultMessage;

    AccountErrorCode(HttpStatus status, String defaultMessage) {
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
