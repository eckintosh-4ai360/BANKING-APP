package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A staff member who collects in the field. The cash they carry is the balance of their (balance-checked) cash with
 * collectors ledger account: collections raise it, remittances to a teller lower it.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "field_officer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldOfficer {

    public enum Status { ACTIVE, SUSPENDED }

    @Id
    @Column(name = "staff_id")
    private UUID staffId;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "collector_ledger_account_id", nullable = false, updatable = false)
    private UUID collectorLedgerAccountId;

    @Column(name = "daily_target", precision = 19, scale = 4)
    private BigDecimal dailyTarget;

    @Column(name = "max_offline_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal maxOfflineAmount;

    @Column(name = "max_offline_hours", nullable = false)
    private int maxOfflineHours;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public FieldOfficer(UUID staffId, UUID tenantId, UUID branchId, String currency, UUID collectorLedgerAccountId,
                        BigDecimal dailyTarget, BigDecimal maxOfflineAmount, int maxOfflineHours, Instant now,
                        UUID createdBy) {
        this.staffId = staffId;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.currency = currency;
        this.collectorLedgerAccountId = collectorLedgerAccountId;
        this.dailyTarget = dailyTarget;
        this.maxOfflineAmount = maxOfflineAmount;
        this.maxOfflineHours = maxOfflineHours;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.createdBy = createdBy;
        this.updatedAt = now;
    }

    public void changeLimits(BigDecimal dailyTarget, BigDecimal maxOfflineAmount, int maxOfflineHours, Instant now) {
        this.dailyTarget = dailyTarget;
        this.maxOfflineAmount = maxOfflineAmount;
        this.maxOfflineHours = maxOfflineHours;
        this.updatedAt = now;
    }

    public void changeStatus(Status status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }
}
