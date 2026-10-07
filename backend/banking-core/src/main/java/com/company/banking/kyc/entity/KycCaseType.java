package com.company.banking.kyc.entity;

public enum KycCaseType {
    /** First verification of a new customer. */
    ONBOARDING,
    /** Scheduled re-verification of an existing customer. */
    PERIODIC_REVIEW,
    /** Verification to a higher tier. */
    UPGRADE,
    /** Change of verified identity data (name, date of birth, documents). */
    UPDATE
}
