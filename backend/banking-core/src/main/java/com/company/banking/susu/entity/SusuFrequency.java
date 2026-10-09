package com.company.banking.susu.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.springframework.data.domain.Persistable;

/**
 * How often a susu plan expects contributions (every 1 day, 1 week, 1 month, ...). Institution configuration.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "susu_frequency")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SusuFrequency implements Persistable<SusuFrequency.Key> {

    public enum Unit { DAY, WEEK, MONTH }

    @Embeddable
    public record Key(@Column(name = "tenant_id") UUID tenantId, @Column(name = "code") String code)
            implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit", nullable = false, updatable = false, length = 5)
    private Unit intervalUnit;

    @Column(name = "interval_count", nullable = false, updatable = false)
    private int intervalCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public SusuFrequency(UUID tenantId, String code, String name, Unit intervalUnit, int intervalCount,
                         Instant createdAt) {
        this.id = new Key(tenantId, code);
        this.name = name;
        this.intervalUnit = intervalUnit;
        this.intervalCount = intervalCount;
        this.active = true;
        this.createdAt = createdAt;
    }

    public String getCode() {
        return id.code();
    }

    /**
     * The due date of the {@code n}-th contribution (1-based) of a plan starting on {@code start}.
     */
    public LocalDate dueDate(LocalDate start, int n) {
        long steps = (long) (n - 1) * intervalCount;
        return switch (intervalUnit) {
            case DAY -> start.plusDays(steps);
            case WEEK -> start.plusWeeks(steps);
            case MONTH -> start.plusMonths(steps);
        };
    }

    public void changeActive(boolean active) {
        this.active = active;
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
