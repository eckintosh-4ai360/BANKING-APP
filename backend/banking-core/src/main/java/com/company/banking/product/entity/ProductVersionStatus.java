package com.company.banking.product.entity;

/**
 * DRAFT is editable; PUBLISHED is offered to new accounts and never changes; RETIRED serves existing accounts only.
 */
public enum ProductVersionStatus {
    DRAFT,
    PUBLISHED,
    RETIRED
}
