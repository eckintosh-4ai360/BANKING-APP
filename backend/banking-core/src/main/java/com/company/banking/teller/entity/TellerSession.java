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
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A teller working one drawer. Cash transactions are only possible while it is OPEN. Closing compares the counted
 * cash with the drawer's ledger balance; a difference waits (BALANCING) for a supervisor to accept it.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "teller_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TellerSession {

    public enum Status { OPEN, BALANCING, CLOSED, CLOSED_WITH_DIFFERENCE }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "branch_id", nullable = false, updatable = false)
    private UUID branchId;

    @Column(name = "cash_drawer_id", nullable = false, updatable = false)
    private UUID cashDrawerId;

    @Column(name = "teller_id", nullable = false, updatable = false)
    private UUID tellerId;

    @Column(name = "business_date", nullable = false, updatable = false)
    private LocalDate businessDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 25)
    private Status status;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "opening_balance", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal openingBalance;

    @Column(name = "expected_closing_balance", precision = 19, scale = 4)
    private BigDecimal expectedClosingBalance;

    @Column(name = "counted_balance", precision = 19, scale = 4)
    private BigDecimal countedBalance;

    @Column(name = "difference", precision = 19, scale = 4)
    private BigDecimal difference;

    @Column(name = "opened_at", nullable = false, updatable = false)
    private Instant openedAt;

    @Column(name = "closing_requested_at")
    private Instant closingRequestedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "supervisor_id")
    private UUID supervisorId;

    @Column(name = "difference_journal_id")
    private UUID differenceJournalId;

    @Column(name = "close_note", length = 300)
    private String closeNote;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public TellerSession(UUID id, UUID tenantId, UUID branchId, UUID cashDrawerId, UUID tellerId,
                         LocalDate businessDate, String currency, BigDecimal openingBalance, Instant openedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.branchId = branchId;
        this.cashDrawerId = cashDrawerId;
        this.tellerId = tellerId;
        this.businessDate = businessDate;
        this.currency = currency;
        this.openingBalance = openingBalance;
        this.openedAt = openedAt;
        this.status = Status.OPEN;
    }

    /**
     * Records the teller's count against the ledger. Balanced: closed; otherwise waiting for a supervisor.
     */
    public void count(BigDecimal expected, BigDecimal counted, String note, Instant at) {
        this.expectedClosingBalance = expected;
        this.countedBalance = counted;
        this.difference = counted.subtract(expected);
        this.closeNote = note;
        this.closingRequestedAt = at;
        if (difference.signum() == 0) {
            this.status = Status.CLOSED;
            this.closedAt = at;
        } else {
            this.status = Status.BALANCING;
        }
    }

    public void acceptDifference(UUID supervisor, UUID journalId, String note, Instant at) {
        this.status = Status.CLOSED_WITH_DIFFERENCE;
        this.supervisorId = supervisor;
        this.differenceJournalId = journalId;
        this.closeNote = note;
        this.closedAt = at;
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }
}
