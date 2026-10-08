package com.company.banking.transaction.model;

/**
 * A transaction is POSTED when its journal is in the ledger; REVERSED once a mirror journal has undone it.
 */
public enum TransactionStatus {
    POSTED,
    REVERSED
}
