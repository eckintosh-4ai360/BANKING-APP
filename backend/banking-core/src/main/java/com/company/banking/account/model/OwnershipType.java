package com.company.banking.account.model;

/**
 * Who may operate the account: one person, joint holders who may each act alone (ANY) or only together (ALL), or a
 * business through its signatories.
 */
public enum OwnershipType {
    SINGLE,
    JOINT_ANY,
    JOINT_ALL,
    BUSINESS
}
