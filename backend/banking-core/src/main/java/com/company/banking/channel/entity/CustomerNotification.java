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
 * A message in the customer's in-app inbox. Only whether it was read ever changes.
 */
@Getter
@Entity
@DynamicUpdate
@Table(name = "customer_notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerNotification {

    public enum Category { TRANSACTION, SECURITY, LOAN, ACCOUNT, GENERAL }

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, updatable = false, length = 15)
    private Category category;

    @Column(name = "title", nullable = false, updatable = false, length = 100)
    private String title;

    @Column(name = "body", nullable = false, updatable = false, length = 500)
    private String body;

    @Column(name = "reference_type", updatable = false, length = 30)
    private String referenceType;

    @Column(name = "reference_id", updatable = false)
    private UUID referenceId;

    /** What it was made from (an outbox event, a reminder); one notification per customer and source. */
    @Column(name = "source_key", updatable = false)
    private UUID sourceKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @SuppressWarnings("java:S107")
    public CustomerNotification(UUID id, UUID tenantId, UUID customerId, Category category, String title, String body,
                                String referenceType, UUID referenceId, UUID sourceKey, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.category = category;
        this.title = title;
        this.body = body;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.sourceKey = sourceKey;
        this.createdAt = createdAt;
    }

    public void markRead(Instant now) {
        if (readAt == null) {
            this.readAt = now;
        }
    }
}
