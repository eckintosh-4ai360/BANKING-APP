package com.company.banking.product.model;

/**
 * The money movements a product can charge for. The charge is taken from the account being debited (for deposits,
 * the account receiving the money).
 */
public enum ChargeEvent {
    CASH_DEPOSIT,
    CASH_WITHDRAWAL,
    TRANSFER_OUT
}
