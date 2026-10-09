package com.company.banking.teller.entity;

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
 * Cash moving between vaults, drawers and the bank. Requested by one person and approved by another; a movement
 * between branches travels through cash in transit until the receiving branch confirms it.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "cash_movement")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashMovement {

    public enum Type { VAULT_TO_DRAWER, DRAWER_TO_VAULT, VAULT_TO_VAULT, BANK_TO_VAULT, VAULT_TO_BANK }

    public enum Status { REQUESTED, IN_TRANSIT, COMPLETED, REJECTED, CANCELLED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "reference", nullable = false, updatable = false, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false, updatable = false, length = 20)
    private Type movementType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 12)
    private Status status;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "amount", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "from_branch_id", nullable = false, updatable = false)
    private UUID fromBranchId;

    @Column(name = "to_branch_id", nullable = false, updatable = false)
    private UUID toBranchId;

    @Column(name = "from_vault_id", updatable = false)
    private UUID fromVaultId;

    @Column(name = "from_drawer_id", updatable = false)
    private UUID fromDrawerId;

    @Column(name = "to_vault_id", updatable = false)
    private UUID toVaultId;

    @Column(name = "to_drawer_id", updatable = false)
    private UUID toDrawerId;

    @Column(name = "note", updatable = false, length = 300)
    private String note;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private UUID requestedBy;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "dispatch_journal_id")
    private UUID dispatchJournalId;

    @Column(name = "received_by")
    private UUID receivedBy;

    @Column(name = "received_at")
    private Instant receivedAt;

    @Column(name = "receipt_journal_id")
    private UUID receiptJournalId;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /**
     * The places the cash leaves and reaches (null where the bank is the other side).
     */
    public record Ends(UUID fromBranchId, UUID toBranchId, UUID fromVaultId, UUID fromDrawerId, UUID toVaultId,
                       UUID toDrawerId) {
    }

    @SuppressWarnings("java:S107")
    public CashMovement(UUID id, UUID tenantId, String reference, Type movementType, String currency,
                        BigDecimal amount, Ends ends, String note, UUID requestedBy, Instant requestedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.movementType = movementType;
        this.status = Status.REQUESTED;
        this.currency = currency;
        this.amount = amount;
        this.fromBranchId = ends.fromBranchId();
        this.toBranchId = ends.toBranchId();
        this.fromVaultId = ends.fromVaultId();
        this.fromDrawerId = ends.fromDrawerId();
        this.toVaultId = ends.toVaultId();
        this.toDrawerId = ends.toDrawerId();
        this.note = note;
        this.requestedBy = requestedBy;
        this.requestedAt = requestedAt;
    }

    /**
     * Approved and posted: the cash left its source. Movements between branches are then in transit.
     */
    public void approve(UUID by, Instant at, UUID journalId) {
        this.approvedBy = by;
        this.approvedAt = at;
        this.dispatchJournalId = journalId;
        this.status = movementType == Type.VAULT_TO_VAULT ? Status.IN_TRANSIT : Status.COMPLETED;
    }

    public void receive(UUID by, Instant at, UUID journalId) {
        this.receivedBy = by;
        this.receivedAt = at;
        this.receiptJournalId = journalId;
        this.status = Status.COMPLETED;
    }

    public void reject(UUID by, Instant at, String reason) {
        this.approvedBy = by;
        this.approvedAt = at;
        this.rejectionReason = reason;
        this.status = Status.REJECTED;
    }

    public void cancel(String reason) {
        this.rejectionReason = reason;
        this.status = Status.CANCELLED;
    }
}
