package com.company.banking.ledger.model;

/**
 * The side on which an account's balance increases.
 */
public enum NormalSide {
    DEBIT,
    CREDIT;

    /**
     * The direction that increases a balance kept on this side.
     */
    public EntryDirection increasingDirection() {
        return this == DEBIT ? EntryDirection.DEBIT : EntryDirection.CREDIT;
    }
}
