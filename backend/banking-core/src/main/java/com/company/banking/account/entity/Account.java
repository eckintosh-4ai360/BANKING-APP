package com.company.banking.account.entity;

import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.model.OwnershipType;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A customer deposit account. Its money lives in the ledger account it points to; this row carries the contract
 * (product version), the holders and the lifecycle. Only the lifecycle columns are writable by the application.
 */
@Getter
@Entity
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Account extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "account_number", nullable = false, updatable = false, length = 20)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "product_id", nullable = false, updatable = false)
    private UUID productId;

    @Column(name = "product_version_id", nullable = false, updatable = false)
    private UUID productVersionId;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "title", nullable = false, length = 150)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private AccountStatus status;

    @Column(name = "status_reason", length = 300)
    private String statusReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "ownership_type", nullable = false, updatable = false, length = 12)
    private OwnershipType ownershipType;

    @Column(name = "opened_on", nullable = false, updatable = false)
    private LocalDate openedOn;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    public Account(UUID id, UUID tenantId, String accountNumber, UUID customerId, UUID productId,
                   UUID productVersionId, UUID ledgerAccountId, UUID branchId, String currency, String title,
                   OwnershipType ownershipType, LocalDate openedOn, boolean awaitingFunding, Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.productVersionId = productVersionId;
        this.ledgerAccountId = ledgerAccountId;
        this.branchId = branchId;
        this.currency = currency;
        this.title = title;
        this.ownershipType = ownershipType;
        this.openedOn = openedOn;
        this.status = awaitingFunding ? AccountStatus.PENDING : AccountStatus.ACTIVE;
        this.activatedAt = awaitingFunding ? null : now;
    }

    public void changeStatus(AccountStatus target, String reason, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "The account cannot move from " + status + " to " + target + ".");
        }
        if (target == AccountStatus.ACTIVE && activatedAt == null) {
            activatedAt = now;
        }
        this.status = target;
        this.statusReason = reason;
    }

    public void close(LocalDate on, String reason, Instant now) {
        changeStatus(AccountStatus.CLOSED, reason, now);
        this.closedOn = on;
    }

    public void recordActivity(Instant at) {
        this.lastActivityAt = at;
    }
}
