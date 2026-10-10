package com.company.banking.channel.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * A destination a customer saved. Until {@code cooldownUntil} transfers to it are capped by the institution's
 * cooldown limit.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "beneficiary")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Beneficiary {

    public enum Type { INTERNAL, BANK, MOBILE_MONEY }

    public enum Status { ACTIVE, REMOVED }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "beneficiary_type", nullable = false, updatable = false, length = 15)
    private Type beneficiaryType;

    @Column(name = "nickname", nullable = false, length = 60)
    private String nickname;

    @Column(name = "account_id", updatable = false)
    private UUID accountId;

    /** The holder's name as verified when saved, partly hidden. */
    @Column(name = "display_name", nullable = false, updatable = false, length = 100)
    private String displayName;

    @Column(name = "favourite", nullable = false)
    private boolean favourite;

    @Column(name = "transfer_limit", precision = 19, scale = 4)
    private BigDecimal transferLimit;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status;

    @Column(name = "cooldown_until", nullable = false, updatable = false)
    private Instant cooldownUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @SuppressWarnings("java:S107")
    public Beneficiary(UUID id, UUID tenantId, UUID customerId, Type type, String nickname, UUID accountId,
                       String displayName, BigDecimal transferLimit, Instant now, Instant cooldownUntil) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.beneficiaryType = type;
        this.nickname = nickname;
        this.accountId = accountId;
        this.displayName = displayName;
        this.transferLimit = transferLimit;
        this.status = Status.ACTIVE;
        this.createdAt = now;
        this.cooldownUntil = cooldownUntil;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean isCoolingDown(Instant now) {
        return cooldownUntil.isAfter(now);
    }

    /**
     * What may be changed without risk: the nickname, the favourite mark, and the limit lowered or raised (never the
     * destination, which needs a new beneficiary and a new cooldown).
     */
    public void edit(String nickname, boolean favourite, BigDecimal transferLimit) {
        this.nickname = nickname;
        this.favourite = favourite;
        this.transferLimit = transferLimit;
    }

    public void remove(Instant now) {
        this.status = Status.REMOVED;
        this.removedAt = now;
    }
}
