package com.company.banking.account.model;

/**
 * ACTIVE reserves funds; the other states end the hold for good (released by staff, consumed by the payment it
 * reserved for, or expired).
 */
public enum HoldStatus {
    ACTIVE,
    RELEASED,
    CONSUMED,
    EXPIRED
}
