package com.company.banking.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record IdentificationTypeId(
        @Column(name = "tenant_id") UUID tenantId,
        @Column(name = "code", length = 30) String code) implements Serializable {
}
