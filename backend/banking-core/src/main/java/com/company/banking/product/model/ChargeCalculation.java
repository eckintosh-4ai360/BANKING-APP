package com.company.banking.product.model;

/**
 * FLAT charges a fixed amount; PERCENT charges a percentage of the amount moved, rounded half up to the currency
 * minor unit and kept within the optional minimum and maximum.
 */
public enum ChargeCalculation {
    FLAT,
    PERCENT
}
