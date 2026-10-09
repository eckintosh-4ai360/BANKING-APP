package com.company.banking.fieldops.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
 * A phone registered to a field officer. It numbers the collections it records 1, 2, 3, ...; the server keeps the
 * highest number received, and a number below it that never arrived is a gap.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "field_device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FieldDevice {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "officer_id", nullable = false, updatable = false)
    private UUID officerId;

    @Column(name = "device_key", nullable = false, updatable = false, length = 100)
    private String deviceKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "last_sequence_no", nullable = false)
    private long lastSequenceNo;

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoked_by")
    private UUID revokedBy;

    @Column(name = "revoke_reason", length = 300)
    private String revokeReason;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public FieldDevice(UUID id, UUID tenantId, UUID officerId, String deviceKey, String name, Instant registeredAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.officerId = officerId;
        this.deviceKey = deviceKey;
        this.name = name;
        this.registeredAt = registeredAt;
    }

    public void recordSync(long highestSequenceNo, Instant at) {
        this.lastSequenceNo = Math.max(lastSequenceNo, highestSequenceNo);
        this.lastSyncedAt = at;
    }

    public void revoke(Instant at, UUID by, String reason) {
        this.revokedAt = at;
        this.revokedBy = by;
        this.revokeReason = reason;
    }

    public boolean isLive() {
        return revokedAt == null;
    }
}
