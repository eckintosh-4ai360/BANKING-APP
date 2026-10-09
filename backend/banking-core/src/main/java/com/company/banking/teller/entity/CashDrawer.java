package com.company.banking.teller.entity;

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
import org.hibernate.annotations.DynamicUpdate;

/**
 * A teller's till for one currency. Its expected cash is always the balance of its (balance-checked) ledger account.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "cash_drawer")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashDrawer {

    public enum Status { ACTIVE, INACTIVE, CLOSED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public CashDrawer(UUID id, UUID tenantId, UUID branchId, String currency, String code, String name,
                      UUID ledgerAccountId, Instant createdAt, UUID createdBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.currency = currency;
        this.code = code;
        this.name = name;
        this.ledgerAccountId = ledgerAccountId;
        this.status = Status.ACTIVE;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }
}
