package com.company.banking.fieldops.entity;

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
 * Something about an officer's collections that a supervisor should look at.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "field_alert")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldAlert {

    public enum Type {
        /** Collections numbered below the device's highest number never arrived. */
        SEQUENCE_GAP,
        /** A different collection or visit arrived under a reference or number already used. */
        CONFLICT,
        /** Collections arrived later than the officer's offline time limit. */
        LATE_SYNC,
        /** One sync brought more cash than the officer may hold offline. */
        OFFLINE_LIMIT
    }

    public enum Status { OPEN, RESOLVED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "officer_id", nullable = false, updatable = false)
    private UUID officerId;

    @Column(name = "device_id", updatable = false)
    private UUID deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "alert_type", nullable = false, updatable = false, length = 20)
    private Type alertType;

    @Column(name = "detail", nullable = false, updatable = false, length = 500)
    private String detail;

    @Column(name = "missing_from", updatable = false)
    private Long missingFrom;

    @Column(name = "missing_to", updatable = false)
    private Long missingTo;

    @Column(name = "client_reference", updatable = false)
    private UUID clientReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "raised_at", nullable = false, updatable = false)
    private Instant raisedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by")
    private UUID resolvedBy;

    @Column(name = "resolution", length = 300)
    private String resolution;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public FieldAlert(UUID id, UUID tenantId, UUID officerId, UUID deviceId, Type alertType, String detail,
                      Long missingFrom, Long missingTo, UUID clientReference, Instant raisedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.officerId = officerId;
        this.deviceId = deviceId;
        this.alertType = alertType;
        this.detail = detail;
        this.missingFrom = missingFrom;
        this.missingTo = missingTo;
        this.clientReference = clientReference;
        this.status = Status.OPEN;
        this.raisedAt = raisedAt;
    }

    public void resolve(Instant at, UUID by, String resolution) {
        this.status = Status.RESOLVED;
        this.resolvedAt = at;
        this.resolvedBy = by;
        this.resolution = resolution;
    }
}
