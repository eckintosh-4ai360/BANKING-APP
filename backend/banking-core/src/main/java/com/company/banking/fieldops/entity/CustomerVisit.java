package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * A field officer's visit to a customer, recorded on the device (idempotent on its client reference).
 */
@Getter
@Entity
@Immutable
@Table(name = "customer_visit")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerVisit {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "client_reference", nullable = false)
    private UUID clientReference;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "officer_id", nullable = false)
    private UUID officerId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "purpose", nullable = false, length = 20)
    private String purpose;

    @Column(name = "outcome", nullable = false, length = 20)
    private String outcome;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "visited_at", nullable = false)
    private Instant visitedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Builder
    @SuppressWarnings("java:S107")
    private CustomerVisit(UUID id, UUID tenantId, UUID clientReference, UUID deviceId, UUID officerId,
                          UUID customerId, String purpose, String outcome, String notes, Instant visitedAt,
                          Instant receivedAt, BigDecimal latitude, BigDecimal longitude, String contentHash) {
        this.id = id;
        this.tenantId = tenantId;
        this.clientReference = clientReference;
        this.deviceId = deviceId;
        this.officerId = officerId;
        this.customerId = customerId;
        this.purpose = purpose;
        this.outcome = outcome;
        this.notes = notes;
        this.visitedAt = visitedAt;
        this.receivedAt = receivedAt;
        this.latitude = latitude;
        this.longitude = longitude;
        this.contentHash = contentHash;
    }
}
