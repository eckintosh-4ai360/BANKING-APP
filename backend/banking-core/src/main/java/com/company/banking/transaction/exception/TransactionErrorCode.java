package com.company.banking.transaction.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum TransactionErrorCode implements ErrorCode {

    ACCOUNT_NOT_CREDITABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The account cannot receive money in its current status."),
    ACCOUNT_NOT_DEBITABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The account cannot pay out money in its current status."),
    WITHDRAWAL_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,
            "The amount is above the largest single withdrawal the product allows."),
    DAILY_LIMIT_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,
            "The amount would take the account over its daily withdrawal limit."),
    MINIMUM_BALANCE_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "The account must keep its minimum operating balance."),
    MAXIMUM_BALANCE_EXCEEDED(HttpStatus.UNPROCESSABLE_CONTENT,
            "The deposit would take the account over the maximum balance of its product."),
    SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_CONTENT, "A transfer needs two different accounts."),
    CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Both accounts of a transfer must use the same currency."),
    TRANSACTION_ALREADY_REVERSED(HttpStatus.UNPROCESSABLE_CONTENT, "The transaction has already been reversed."),
    REVERSAL_NOT_SUPPORTED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Loan disbursements and repayments are corrected through the loan, not reversed here.");

    private final HttpStatus status;
    private final String defaultMessage;

    TransactionErrorCode(HttpStatus status, String defaultMessage) {
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
