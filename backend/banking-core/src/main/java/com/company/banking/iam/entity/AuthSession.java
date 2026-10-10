package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A login session. Its id travels in every access token ({@code sid}) and is checked on each request, which makes
 * logout, password change and administrative revocation effective immediately.
 */
@Getter
@Entity
@Table(name = "auth_session")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSession {

    @Id
    private UUID id;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "principal_type", nullable = false, updatable = false, length = 20)
    private PrincipalType principalType;

    @Column(name = "principal_id", nullable = false, updatable = false)
    private UUID principalId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_refreshed_at", nullable = false)
    private Instant lastRefreshedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "revoke_reason", length = 50)
    private String revokeReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    /** The customer's trusted device the session was opened on (customers only). */
    @Column(name = "device_id", updatable = false)
    private UUID deviceId;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public AuthSession(UUID id, UUID tenantId, PrincipalType principalType, UUID principalId, Instant now,
                       Instant expiresAt, String ipAddress, String userAgent) {
        this(id, tenantId, principalType, principalId, null, now, expiresAt, ipAddress, userAgent);
    }

    @SuppressWarnings("java:S107")
    public AuthSession(UUID id, UUID tenantId, PrincipalType principalType, UUID principalId, UUID deviceId,
                       Instant now, Instant expiresAt, String ipAddress, String userAgent) {
        this.deviceId = deviceId;
        this.id = id;
        this.tenantId = tenantId;
        this.principalType = principalType;
        this.principalId = principalId;
        this.status = SessionStatus.ACTIVE;
        this.createdAt = now;
        this.lastRefreshedAt = now;
        this.expiresAt = expiresAt;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
    }

    public boolean isUsable(Instant now) {
        return status == SessionStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void markRefreshed(Instant now) {
        this.lastRefreshedAt = now;
    }

    public void revoke(String reason, Instant now) {
        if (status == SessionStatus.REVOKED) {
            return;
        }
        this.status = SessionStatus.REVOKED;
        this.revokedAt = now;
        this.revokeReason = reason;
    }
}
