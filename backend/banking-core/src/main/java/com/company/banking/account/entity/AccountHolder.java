package com.company.banking.account.entity;

import com.company.banking.account.model.HolderRole;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

/**
 * A customer's relationship to an account. Holders are never deleted; removing one sets {@code removedAt}.
 */
@Getter
@Entity
@Table(name = "account_holder")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccountHolder implements Persistable<AccountHolderId> {

    @EmbeddedId
    private AccountHolderId id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_role", nullable = false, updatable = false, length = 12)
    private HolderRole role;

    @Column(name = "added_at", nullable = false, updatable = false)
    private Instant addedAt;

    @Column(name = "added_by", updatable = false)
    private UUID addedBy;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Transient
    @Getter(AccessLevel.NONE)
    private boolean newEntity = true;

    public AccountHolder(UUID tenantId, UUID accountId, UUID customerId, HolderRole role, Instant addedAt,
                         UUID addedBy) {
        this.id = new AccountHolderId(accountId, customerId);
        this.tenantId = tenantId;
        this.role = role;
        this.addedAt = addedAt;
        this.addedBy = addedBy;
    }

    public UUID getCustomerId() {
        return id.customerId();
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
