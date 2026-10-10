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
 * A one-time code texted to a phone for one purpose. Only a keyed hash of the code is kept; it expires, allows a
 * few attempts and is good for one use. A challenge answering a request that matched no one has no code at all.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "otp_challenge")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OtpChallenge {

    public enum Purpose { ACTIVATION, DEVICE_BINDING, PASSWORD_RESET, PIN_RESET, REGISTRATION }

    public enum Status { PENDING, VERIFIED, FAILED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", updatable = false)
    private UUID customerId;

    @Column(name = "phone", nullable = false, updatable = false, length = 20)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, updatable = false, length = 20)
    private Purpose purpose;

    @Column(name = "device_key", updatable = false, length = 100)
    private String deviceKey;

    @Getter(AccessLevel.NONE)
    @Column(name = "code_hash", updatable = false, length = 64)
    private String codeHash;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "max_attempts", nullable = false, updatable = false)
    private int maxAttempts;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @SuppressWarnings("java:S107")
    public OtpChallenge(UUID id, UUID tenantId, UUID customerId, String phone, Purpose purpose, String deviceKey,
                        String codeHash, int maxAttempts, Instant now, Instant expiresAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.phone = phone;
        this.purpose = purpose;
        this.deviceKey = deviceKey;
        this.codeHash = codeHash;
        this.maxAttempts = maxAttempts;
        this.status = Status.PENDING;
        this.createdAt = now;
        this.expiresAt = expiresAt;
    }

    public String codeHash() {
        return codeHash;
    }

    public boolean isOpen(Instant now) {
        return status == Status.PENDING && expiresAt.isAfter(now);
    }

    /**
     * Counts a wrong answer; the challenge fails when the attempts run out.
     */
    public void registerWrongCode() {
        this.attempts++;
        if (attempts >= maxAttempts) {
            this.status = Status.FAILED;
        }
    }

    public void markVerified(Instant now) {
        this.attempts++;
        this.status = Status.VERIFIED;
        this.verifiedAt = now;
    }
}
