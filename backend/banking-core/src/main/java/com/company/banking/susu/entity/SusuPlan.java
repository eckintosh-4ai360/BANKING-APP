package com.company.banking.susu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A customer's commitment to contribute a fixed amount at a fixed frequency into their susu account, in cycles of
 * {@code cycleLength} contributions; at the end of each cycle {@code commissionContributions} of them are the
 * collector's commission.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "susu_plan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SusuPlan {

    public enum Status { ACTIVE, COMPLETED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "plan_number", nullable = false, updatable = false, length = 30)
    private String planNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /** The account's branch. */
    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "frequency_code", nullable = false, updatable = false, length = 20)
    private String frequencyCode;

    @Column(name = "contribution_amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal contributionAmount;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "cycle_length", nullable = false, updatable = false)
    private int cycleLength;

    @Column(name = "commission_contributions", nullable = false, updatable = false)
    private int commissionContributions;

    @Column(name = "start_date", nullable = false, updatable = false)
    private LocalDate startDate;

    @Column(name = "end_date", updatable = false)
    private LocalDate endDate;

    @Column(name = "target_amount", updatable = false, precision = 19, scale = 4)
    private BigDecimal targetAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "current_cycle", nullable = false)
    private int currentCycle;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 300)
    private String closeReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Builder
    @SuppressWarnings("java:S107")
    private SusuPlan(UUID id, UUID tenantId, String planNumber, UUID customerId, UUID accountId, UUID branchId,
                     String frequencyCode,
                     BigDecimal contributionAmount, String currency, int cycleLength, int commissionContributions,
                     LocalDate startDate, LocalDate endDate, BigDecimal targetAmount, Instant createdAt,
                     UUID createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.planNumber = planNumber;
        this.customerId = customerId;
        this.accountId = accountId;
        this.branchId = branchId;
        this.frequencyCode = frequencyCode;
        this.contributionAmount = contributionAmount;
        this.currency = currency;
        this.cycleLength = cycleLength;
        this.commissionContributions = commissionContributions;
        this.startDate = startDate;
        this.endDate = endDate;
        this.targetAmount = targetAmount;
        this.status = Status.ACTIVE;
        this.currentCycle = 1;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    /** The sequence number of the first contribution of a cycle. */
    public int firstOfCycle(int cycle) {
        return (cycle - 1) * cycleLength + 1;
    }

    public void startCycle(int cycle) {
        this.currentCycle = cycle;
    }

    public void close(Status status, Instant at, String reason) {
        this.status = status;
        this.closedAt = at;
        this.closeReason = reason;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
