package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

/**
 * One refresh token of a session, stored only as a SHA-256 hash (the token itself has 256 bits of entropy, so a
 * fast hash is sufficient). Tokens are single-use: rotation sets {@code rotatedAt}, and presenting a rotated token
 * again is treated as theft.
 */
@Getter
@Entity
@Table(name = "auth_refresh_token")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken implements Persistable<String> {

    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "tenant_id", updatable = false)
    private UUID tenantId;

    @Column(name = "session_id", nullable = false, updatable = false)
    private UUID sessionId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "rotated_at")
    private Instant rotatedAt;

    @Transient
    private boolean newEntity = true;

    public RefreshToken(String tokenHash, UUID tenantId, UUID sessionId, Instant issuedAt, Instant expiresAt) {
        this.tokenHash = tokenHash;
        this.tenantId = tenantId;
        this.sessionId = sessionId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
    }

    @Override
    public String getId() {
        return tokenHash;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }

    public boolean isRotated() {
        return rotatedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void markRotated(Instant now) {
        this.rotatedAt = now;
    }
}
