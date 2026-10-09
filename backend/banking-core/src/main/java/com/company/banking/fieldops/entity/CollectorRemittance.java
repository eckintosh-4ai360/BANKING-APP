package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Cash a field officer handed to a teller, counted and received into the teller's drawer. Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "collector_remittance")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorRemittance {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "reference", nullable = false, length = 40)
    private String reference;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Column(name = "teller_id", nullable = false)
    private UUID tellerId;

    @Column(name = "teller_session_id", nullable = false)
    private UUID tellerSessionId;

    @Column(name = "cash_drawer_id", nullable = false)
    private UUID cashDrawerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "journal_entry_id", nullable = false)
    private UUID journalEntryId;

    @Column(name = "business_date", nullable = false)
    private LocalDate businessDate;

    @Column(name = "note", length = 300)
    private String note;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Builder
    @SuppressWarnings("java:S107")
    private CollectorRemittance(UUID id, UUID tenantId, String reference, UUID officerId, UUID tellerId,
                                UUID tellerSessionId, UUID cashDrawerId, UUID branchId, BigDecimal amount,
                                String currency, UUID journalEntryId, LocalDate businessDate, String note,
                                String idempotencyKey, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.reference = reference;
        this.officerId = officerId;
        this.tellerId = tellerId;
        this.tellerSessionId = tellerSessionId;
        this.cashDrawerId = cashDrawerId;
        this.branchId = branchId;
        this.amount = amount;
        this.currency = currency;
        this.journalEntryId = journalEntryId;
        this.businessDate = businessDate;
        this.note = note;
        this.idempotencyKey = idempotencyKey;
        this.createdAt = createdAt;
    }
}
