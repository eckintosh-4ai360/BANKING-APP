package com.company.banking.account.entity;

import com.company.banking.account.model.HoldStatus;
import com.company.banking.account.model.HoldType;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
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

/**
 * Funds reserved on an account. A database trigger adds active holds into the hold amount of the balance row, which
 * lowers the available balance; ending the hold gives the funds back. The amount never changes.
 */
@Getter
@Entity
@Table(name = "account_hold")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHold {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "ledger_account_id", nullable = false, updatable = false)
    private UUID ledgerAccountId;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "hold_type", nullable = false, updatable = false, length = 20)
    private HoldType holdType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private HoldStatus status;

    @Column(name = "reason", nullable = false, updatable = false, length = 300)
    private String reason;

    @Column(name = "reference", updatable = false, length = 60)
    private String reference;

    @Column(name = "expires_at", updatable = false)
    private Instant expiresAt;

    @Column(name = "placed_at", nullable = false, updatable = false)
    private Instant placedAt;

    @Column(name = "placed_by", updatable = false)
    private UUID placedBy;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "released_by")
    private UUID releasedBy;

    @Column(name = "release_reason", length = 300)
    private String releaseReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AccountHold(UUID id, UUID tenantId, UUID accountId, UUID ledgerAccountId, BigDecimal amount,
                       HoldType holdType, String reason, String reference, Instant expiresAt, Instant placedAt,
                       UUID placedBy) {
        this.id = id;
        this.tenantId = tenantId;
        this.accountId = accountId;
        this.ledgerAccountId = ledgerAccountId;
        this.amount = amount;
        this.holdType = holdType;
        this.reason = reason;
        this.reference = reference;
        this.expiresAt = expiresAt;
        this.placedAt = placedAt;
        this.placedBy = placedBy;
        this.status = HoldStatus.ACTIVE;
    }

    /**
     * Ends the hold: released by staff, consumed by the payment it reserved funds for, or expired.
     */
    public void end(HoldStatus outcome, String reason, Instant at, UUID by) {
        if (status != HoldStatus.ACTIVE || outcome == HoldStatus.ACTIVE) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION, "The hold is no longer active.");
        }
        this.status = outcome;
        this.releaseReason = reason;
        this.releasedAt = at;
        this.releasedBy = by;
    }
}
