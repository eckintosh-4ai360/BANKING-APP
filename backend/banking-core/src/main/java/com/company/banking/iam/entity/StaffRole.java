package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
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

@Getter
@Entity
@Table(name = "staff_role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StaffRole implements Persistable<StaffRoleId> {

    @EmbeddedId
    private StaffRoleId id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt;

    @Column(name = "assigned_by", updatable = false)
    private UUID assignedBy;

    @Transient
    private boolean newEntity = true;

    public StaffRole(UUID tenantId, UUID staffId, UUID roleId, Instant assignedAt, UUID assignedBy) {
        this.id = new StaffRoleId(staffId, roleId);
        this.tenantId = tenantId;
        this.assignedAt = assignedAt;
        this.assignedBy = assignedBy;
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
