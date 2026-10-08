package com.company.banking.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record AccountHolderId(
        @Column(name = "account_id") UUID accountId,
        @Column(name = "customer_id") UUID customerId) implements Serializable {
}
