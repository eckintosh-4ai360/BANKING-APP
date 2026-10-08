package com.company.banking.ledger.model;

/**
 * What produced a journal.
 */
public enum JournalSource {
    /** A customer or internal financial transaction. */
    TRANSACTION,
    /** A manual journal, posted only after a second person approved it. */
    MANUAL,
    /** The reversal of another journal. */
    REVERSAL,
    /** End-of-day processing (interest, fees). */
    EOD,
    LOAN,
    PROVISION,
    PERIOD_CLOSE,
    OPENING_BALANCE
}
