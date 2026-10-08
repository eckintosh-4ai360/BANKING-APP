package com.company.banking.approval.model;

import com.company.banking.common.security.Permissions;

/**
 * Actions that go through maker-checker. Reversals and manual journals always do; withdrawals and transfers do when
 * the institution sets a threshold for them.
 */
public enum ApprovalType {
    TRANSACTION_REVERSAL(Permissions.TRANSACTION_REVERSE),
    MANUAL_JOURNAL(Permissions.LEDGER_POST),
    CASH_WITHDRAWAL(Permissions.APPROVAL_ACT),
    TRANSFER(Permissions.APPROVAL_ACT);

    private final String checkerPermission;

    ApprovalType(String checkerPermission) {
        this.checkerPermission = checkerPermission;
    }

    /**
     * Besides {@code approval.act}, the checker must hold the permission the action itself needs.
     */
    public String checkerPermission() {
        return checkerPermission;
    }

    public boolean thresholdBased() {
        return this == CASH_WITHDRAWAL || this == TRANSFER;
    }
}
