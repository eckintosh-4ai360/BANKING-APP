package com.company.banking.ledger.model;

/**
 * Kinds of sub-ledger account.
 */
public enum LedgerAccountType {
    CUSTOMER_DEPOSIT,
    LOAN,
    TELLER_DRAWER,
    VAULT,
    COLLECTOR_CASH,
    SETTLEMENT,
    SUSPENSE,
    CASH_IN_TRANSIT,
    INTERNAL
}
