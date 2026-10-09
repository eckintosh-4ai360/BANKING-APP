package com.company.banking.loan.schedule;

/**
 * How a loan's interest is computed (spec risk F7: precise methods, no user-entered formulas).
 */
public enum InterestMethod {

    /** Interest on the original principal for every period; principal repaid in equal parts. */
    FLAT,

    /** Interest on the outstanding principal; equal installments (annuity) while amortising. */
    DECLINING_BALANCE_EQUAL_INSTALLMENT,

    /** Interest on the outstanding principal; principal repaid in equal parts, so installments decline. */
    DECLINING_BALANCE_EQUAL_PRINCIPAL
}
