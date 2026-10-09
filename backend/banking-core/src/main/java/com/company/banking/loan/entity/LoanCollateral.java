package com.company.banking.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * Something pledged against an application's loan. Its forced-sale value counts towards the product's collateral
 * coverage once verified.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "loan_collateral")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LoanCollateral {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "application_id", nullable = false, updatable = false)
    private UUID applicationId;

    @Column(name = "category", nullable = false, updatable = false, length = 30)
    private String category;

    @Column(name = "description", nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "estimated_value", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal estimatedValue;

    @Column(name = "forced_sale_value", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal forcedSaleValue;

    @Column(name = "valuation_date", nullable = false, updatable = false)
    private LocalDate valuationDate;

    @Column(name = "status", nullable = false, length = 10)
    private String status;

    @Column(name = "verified_by")
    private UUID verifiedBy;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @SuppressWarnings("java:S107")
    public LoanCollateral(UUID id, UUID tenantId, UUID applicationId, String category, String description,
                          BigDecimal estimatedValue, BigDecimal forcedSaleValue, LocalDate valuationDate,
                          Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.applicationId = applicationId;
        this.category = category;
        this.description = description;
        this.estimatedValue = estimatedValue;
        this.forcedSaleValue = forcedSaleValue;
        this.valuationDate = valuationDate;
        this.status = "PLEDGED";
        this.createdAt = createdAt;
    }

    public void verify(UUID by, Instant at) {
        this.verifiedBy = by;
        this.verifiedAt = at;
    }

    public void release(Instant at) {
        this.status = "RELEASED";
        this.releasedAt = at;
    }

    public boolean isVerified() {
        return verifiedAt != null;
    }
}
