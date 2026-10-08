package com.company.banking.approval.model;

/**
 * PENDING waits for a checker; APPROVED ran the action; REJECTED and CANCELLED (by the maker) never ran it.
 */
public enum ApprovalStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED
}
