package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A collection that reached the server, as the device recorded it, with what became of it: posted (with its
 * financial transaction) or rejected (with the reason, and the cash still with the officer). Never changes.
 */
@Getter
@Entity
@Immutable
@Table(name = "collection")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection {

    public enum Status { POSTED, REJECTED }

    public enum TargetType { SAVINGS_ACCOUNT, SUSU_PLAN }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_reference", nullable = false)
    private UUID clientReference;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "device_sequence_no", nullable = false)
    private long deviceSequenceNo;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 20)
    private TargetType targetType;

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Column(name = "susu_plan_id")
    private UUID susuPlanId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "collected_at", nullable = false)
    private Instant collectedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "note", length = 200)
    private String note;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "rejection_code", length = 60)
    private String rejectionCode;

    @Column(name = "rejection_reason", length = 300)
    private String rejectionReason;

    @Column(name = "financial_transaction_id")
    private UUID financialTransactionId;

    @Column(name = "transaction_reference", length = 40)
    private String transactionReference;

    @Column(name = "business_date")
    private LocalDate businessDate;

    @Builder
    @SuppressWarnings("java:S107")
    private Collection(UUID id, UUID tenantId, UUID clientReference, UUID deviceId, long deviceSequenceNo,
                       UUID officerId, UUID customerId, TargetType targetType, UUID accountId, UUID susuPlanId,
                       BigDecimal amount, String currency, Instant collectedAt, Instant receivedAt,
                       BigDecimal latitude, BigDecimal longitude, String note, String contentHash, Status status,
                       String rejectionCode, String rejectionReason, UUID financialTransactionId,
                       String transactionReference, LocalDate businessDate) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientReference = clientReference;
        this.deviceId = deviceId;
        this.deviceSequenceNo = deviceSequenceNo;
        this.officerId = officerId;
        this.customerId = customerId;
        this.targetType = targetType;
        this.accountId = accountId;
        this.susuPlanId = susuPlanId;
        this.amount = amount;
        this.currency = currency;
        this.collectedAt = collectedAt;
        this.receivedAt = receivedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.note = note;
        this.contentHash = contentHash;
        this.status = status;
        this.rejectionCode = rejectionCode;
        this.rejectionReason = rejectionReason;
        this.financialTransactionId = financialTransactionId;
        this.transactionReference = transactionReference;
        this.businessDate = businessDate;
    }
}
