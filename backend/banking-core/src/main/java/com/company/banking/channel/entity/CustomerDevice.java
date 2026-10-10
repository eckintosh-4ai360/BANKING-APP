package com.company.banking.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * An app installation a customer trusted (with a code texted to their phone). Sessions are bound to it; revoking it
 * ends them.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerDevice {

    public enum Status { ACTIVE, REVOKED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    /** The installation's own identifier (the app's {@code X-Device-Id}). */
    @Column(name = "device_key", nullable = false, updatable = false, length = 100)
    private String deviceKey;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "platform", length = 20)
    private String platform;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "bound_at", nullable = false, updatable = false)
    private Instant boundAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    /** Where pushes for this installation go ({@code FCM} or {@code APNS}), when the app registered for them. */
    @Column(name = "push_provider", length = 10)
    private String pushProvider;

    @Column(name = "push_token", length = 512)
    private String pushToken;

    @Column(name = "push_updated_at")
    private Instant pushUpdatedAt;

    public CustomerDevice(UUID id, UUID tenantId, UUID customerId, String deviceKey, String name, String platform,
                          Instant now) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.deviceKey = deviceKey;
        this.name = name;
        this.platform = platform;
        this.status = Status.ACTIVE;
        this.boundAt = now;
        this.lastSeenAt = now;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public void seen(Instant now) {
        this.lastSeenAt = now;
    }

    /**
     * @param provider null with a null token to stop pushes
     */
    public void registerPush(String provider, String token, Instant now) {
        this.pushProvider = provider;
        this.pushToken = token;
        this.pushUpdatedAt = now;
    }

    public void revoke(String reason, Instant now) {
        if (status == Status.REVOKED) {
            return;
        }
        this.pushProvider = null;
        this.pushToken = null;
        this.status = Status.REVOKED;
        this.revokedAt = now;
        this.revokeReason = reason;
    }
}
