package com.company.banking.approval.entity;

import com.company.banking.approval.model.ApprovalType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * An institution's threshold: movements of this type at or above the amount (in that currency) need a checker.
 */
@Getter
@Entity
@Table(name = "approval_policy")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ApprovalPolicy implements Persistable<ApprovalPolicy.Key> {

    @Embeddable
    public record Key(
            @Column(name = "tenant_id") UUID tenantId,
            @Enumerated(EnumType.STRING) @Column(name = "request_type", length = 30) ApprovalType requestType,
            @Column(name = "currency", length = 3) String currency) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "threshold_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal thresholdAmount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public ApprovalPolicy(Key id) {
        this.id = id;
    }

    public void configure(BigDecimal thresholdAmount, boolean active, Instant at, UUID by) {
        this.thresholdAmount = thresholdAmount;
        this.active = active;
        this.updatedAt = at;
        this.updatedBy = by;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
