package com.company.banking.ledger.entity;

import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.NormalSide;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A sub-ledger account (customer account, teller drawer, collector cash...). It rolls up into one GL account and
 * lives in one branch and one currency. Its balance is kept in {@code account_balance} by the database.
 */
@Getter
@Entity
@Table(name = "ledger_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LedgerAccount {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "chart_of_account_id", nullable = false, updatable = false)
    private UUID chartOfAccountId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "ledger_account_type", nullable = false, updatable = false, length = 20)
    private LedgerAccountType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "normal_side", nullable = false, updatable = false, length = 6)
    private NormalSide normalSide;

    @Column(name = "balance_check", nullable = false, updatable = false)
    private boolean balanceCheck;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "owner_type", updatable = false, length = 20)
    private String ownerType;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private LedgerAccountStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public LedgerAccount(UUID id, UUID tenantId, UUID chartOfAccountId, UUID branchId, String currency,
                         LedgerAccountType type, NormalSide normalSide, boolean balanceCheck, String name,
                         String ownerType, UUID ownerId, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.chartOfAccountId = chartOfAccountId;
        this.branchId = branchId;
        this.currency = currency;
        this.type = type;
        this.normalSide = normalSide;
        this.balanceCheck = balanceCheck;
        this.name = name;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.status = LedgerAccountStatus.ACTIVE;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public void rename(String name) {
        this.name = name;
    }

    /**
     * The database refuses to close an account that still has a balance or holds.
     */
    public void close(Instant closedAt) {
        this.status = LedgerAccountStatus.CLOSED;
        this.closedAt = closedAt;
    }

    public boolean isActive() {
        return status == LedgerAccountStatus.ACTIVE;
    }
}
