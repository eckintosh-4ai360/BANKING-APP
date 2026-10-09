package com.company.banking.teller.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;
import org.springframework.data.domain.Persistable;

/**
 * A count of the cash in a drawer, note by note. Never changed once recorded.
 */
@Getter
@Entity
@Table(name = "cash_count")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashCount implements Persistable<UUID> {

    public enum Type { OPENING, CLOSING }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "teller_session_id", nullable = false, updatable = false)
    private UUID tellerSessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "count_type", nullable = false, updatable = false, length = 10)
    private Type countType;

    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "denominations", nullable = false, updatable = false, columnDefinition = "jsonb")
    private String denominations;

    @Column(name = "total", nullable = false, updatable = false, precision = 19, scale = 4)
    private BigDecimal total;

    @Column(name = "counted_by", nullable = false, updatable = false)
    private UUID countedBy;

    @Column(name = "counted_at", nullable = false, updatable = false)
    private Instant countedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public CashCount(UUID id, UUID tenantId, UUID tellerSessionId, Type countType, String denominations,
                     BigDecimal total, UUID countedBy, Instant countedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.tellerSessionId = tellerSessionId;
        this.countType = countType;
        this.denominations = denominations;
        this.total = total;
        this.countedBy = countedBy;
        this.countedAt = countedAt;
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
}
