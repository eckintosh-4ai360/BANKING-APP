package com.company.banking.transaction.model;

public enum TransactionType {
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    TRANSFER,
    /** Cash a field officer took for the account (credited from the officer's cash with collectors). */
    FIELD_COLLECTION,
    /** A loan paid out into the borrower's account. */
    LOAN_DISBURSEMENT,
    /** A loan installment paid from the borrower's account or in cash. */
    LOAN_REPAYMENT
}
