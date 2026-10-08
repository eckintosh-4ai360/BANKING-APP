package com.company.banking.ledger.model;

/**
 * Stable keys of the GL accounts the platform posts to by itself. Institutions may rename these accounts or give
 * them their own codes; the system code is how the Posting Engine finds them.
 */
public enum SystemAccount {
    CASH_AT_BRANCH,
    VAULT_CASH,
    COLLECTOR_CASH,
    BANK_BALANCES,
    MOBILE_MONEY_SETTLEMENT,
    LOAN_PRINCIPAL,
    INTEREST_RECEIVABLE,
    LOAN_LOSS_PROVISION,
    INTER_BRANCH_DUE_FROM,
    SUSPENSE_DEBIT,
    SAVINGS_DEPOSITS,
    CURRENT_DEPOSITS,
    SUSU_DEPOSITS,
    FIXED_DEPOSITS,
    TARGET_SAVINGS_DEPOSITS,
    INTEREST_PAYABLE,
    INTER_BRANCH_DUE_TO,
    SUSPENSE_CREDIT,
    SHARE_CAPITAL,
    MEMBER_SHARES,
    STATUTORY_RESERVE,
    RETAINED_EARNINGS,
    CURRENT_YEAR_EARNINGS,
    LOAN_INTEREST_INCOME,
    ACCOUNT_FEE_INCOME,
    TRANSACTION_FEE_INCOME,
    OTHER_INCOME,
    DEPOSIT_INTEREST_EXPENSE,
    PROVISION_EXPENSE,
    OPERATING_EXPENSE
}
