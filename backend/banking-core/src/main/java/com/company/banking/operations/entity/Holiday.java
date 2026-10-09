package com.company.banking.operations.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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
import org.springframework.data.domain.Persistable;

/**
 * A day the institution does not open. Only days after the current business date can be added or removed.
 */
@Getter
@Entity
@Table(name = "holiday")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Holiday implements Persistable<Holiday.Key> {

    @Embeddable
    public record Key(@Column(name = "tenant_id") UUID tenantId,
                      @Column(name = "holiday_date") LocalDate holidayDate) implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "name", nullable = false, updatable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public Holiday(UUID tenantId, LocalDate date, String name, Instant createdAt, UUID createdBy) {
        this.id = new Key(tenantId, date);
        this.name = name;
        this.createdAt = createdAt;
        this.createdBy = createdBy;
    }

    public LocalDate getDate() {
        return id.holidayDate();
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
