package com.company.banking.kyc.exception;

import com.company.banking.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KycErrorCode implements ErrorCode {

    KYC_CASE_ALREADY_OPEN(HttpStatus.CONFLICT, "The customer already has an open KYC case."),
    KYC_CASE_TYPE_NOT_ALLOWED(HttpStatus.UNPROCESSABLE_CONTENT,
            "This type of KYC case does not fit the customer's current state."),
    KYC_TIER_NOT_AVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The KYC tier does not exist or is inactive."),
    KYC_REQUIREMENTS_NOT_MET(HttpStatus.UNPROCESSABLE_CONTENT, "The KYC requirements of the tier are not met."),
    IDENTITY_VERIFICATION_UNAVAILABLE(HttpStatus.UNPROCESSABLE_CONTENT,
            "Electronic identity verification is not available for this document. Record a manual check instead."),
    HIGH_RISK_REQUIRED(HttpStatus.UNPROCESSABLE_CONTENT,
            "A watchlist or PEP hit was recorded; the customer can only be approved as HIGH risk.");

    private final HttpStatus status;
    private final String defaultMessage;

    KycErrorCode(HttpStatus status, String defaultMessage) {
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
