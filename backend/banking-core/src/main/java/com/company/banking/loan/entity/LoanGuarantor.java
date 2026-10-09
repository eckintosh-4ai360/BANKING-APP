package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Someone who guarantees part of an application's loan (optionally an existing customer). Counts towards the
 * product's required guarantors once verified.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_guarantor")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanGuarantor {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "full_name", nullable = false, updatable = false, length = 150)
    private String fullName;

    @Column(name = "phone", updatable = false, length = 30)
    private String phone;

    @Column(name = "relationship", nullable = false, updatable = false, length = 60)
    private String relationship;

    @Column(name = "guaranteed_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal guaranteedAmount;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public LoanGuarantor(UUID id, UUID tenantId, UUID applicationId, UUID customerId, String fullName, String phone,
                         String relationship, BigDecimal guaranteedAmount, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.customerId = customerId;
        this.fullName = fullName;
        this.phone = phone;
        this.relationship = relationship;
        this.guaranteedAmount = guaranteedAmount;
        this.createdAt = createdAt;
    }

    public void verify(UUID by, Instant at) {
        this.verifiedBy = by;
        this.verifiedAt = at;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
