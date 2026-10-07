package com.company.banking.kyc.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record KycTierId(
        @Column(name = "tenant_id") UUID tenantId,
        @Column(name = "code", length = 30) String code) implements Serializable {
}
