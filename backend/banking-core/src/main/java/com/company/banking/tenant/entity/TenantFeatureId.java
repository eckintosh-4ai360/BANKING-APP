package com.company.banking.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record TenantFeatureId(
        @Column(name = "tenant_id") UUID tenantId,
        @Column(name = "feature_code", length = 40) String featureCode) implements Serializable {
}
