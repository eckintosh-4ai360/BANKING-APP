package com.company.banking.transaction.model;

/**
 * Where a transaction came from: staff at a branch (CMS), the customer app, a field officer, or the system.
 */
public enum TransactionChannel {
    BRANCH,
    MOBILE,
    FIELD,
    SYSTEM
}
