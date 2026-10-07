package com.company.banking.customer.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * A KYC document of a customer (the binary lives in the document module) and its review state.
 */
@Getter
@Entity
@Table(name = "customer_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerDocument extends AuditableEntity {

    public static final String PENDING_REVIEW = "PENDING_REVIEW";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String REJECTED = "REJECTED";

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Column(name = "stored_document_id", nullable = false, updatable = false)
    private UUID storedDocumentId;

    @Column(name = "document_type", nullable = false, updatable = false, length = 40)
    private String documentType;

    @Column(name = "review_status", nullable = false, length = 20)
    private String reviewStatus;

    @Column(name = "review_note", length = 255)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    public CustomerDocument(UUID id, UUID tenantId, UUID customerId, UUID storedDocumentId, String documentType) {
        this.id = id;
        this.tenantId = tenantId;
        this.customerId = customerId;
        this.storedDocumentId = storedDocumentId;
        this.documentType = documentType;
        this.reviewStatus = PENDING_REVIEW;
    }

    public void review(boolean accepted, String note, UUID reviewer, Instant now) {
        this.reviewStatus = accepted ? ACCEPTED : REJECTED;
        this.reviewNote = note;
        this.reviewedBy = reviewer;
        this.reviewedAt = now;
    }
}
