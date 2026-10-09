package com.company.banking.loan.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LoanErrorCode implements ErrorCode {

    PRODUCT_CODE_EXISTS(HttpStatus.CONFLICT, "A loan product with this code already exists."),
    PRODUCT_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The loan product is inactive or has no published terms."),
    DRAFT_ALREADY_EXISTS(HttpStatus.CONFLICT, "The loan product already has a draft version."),
    VERSION_NOT_EDITABLE(HttpStatus.UNPROCESSABLE_CONTENT, "Only a draft version can be changed or published."),
    INVALID_TERMS(HttpStatus.UNPROCESSABLE_CONTENT, "The loan terms are not valid."),
    INVALID_GL_MAPPING(HttpStatus.UNPROCESSABLE_CONTENT,
            "A GL account of the loan product is missing, not postable or of the wrong class."),
    CUSTOMER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "Only an active, KYC-approved customer can borrow."),
    KYC_TIER_TOO_LOW(HttpStatus.UNPROCESSABLE_CONTENT, "The customer's KYC tier is below what the loan product needs."),
    OUTSIDE_PRODUCT_LIMITS(HttpStatus.UNPROCESSABLE_CONTENT,
            "The amount or number of installments is outside the loan product's limits."),
    INVALID_DISBURSEMENT_ACCOUNT(HttpStatus.UNPROCESSABLE_CONTENT,
            "Loans are paid into an active account the borrower holds, in the loan's currency."),
    INVALID_STEP(HttpStatus.CONFLICT, "The application is not at a stage where this step applies."),
    SEPARATION_OF_DUTIES(HttpStatus.FORBIDDEN,
            "The loan officer cannot recommend, approve or disburse their own application, and nobody may take two"
                    + " of those steps on one application."),
    GUARANTORS_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "The loan product needs more verified guarantors before approval."),
    COLLATERAL_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "Verified collateral does not cover what the loan product needs."),
    INVALID_FIRST_DUE_DATE(HttpStatus.UNPROCESSABLE_CONTENT,
            "The first due date must be after disbursement and within two repayment periods of it."),
    LOAN_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "The loan is not active."),
    OVERPAYMENT(HttpStatus.UNPROCESSABLE_CONTENT, "The amount is more than the loan's payoff amount."),
    SETTLE_WITH_PAYOFF(HttpStatus.UNPROCESSABLE_CONTENT,
            "The amount would repay all principal; pay the payoff amount to settle the loan instead.");

    private final HttpStatus status;
    private final String defaultMessage;

    LoanErrorCode(HttpStatus status, String defaultMessage) {
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
