package com.company.banking.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/**
 * Metadata of an encrypted object in object storage. Write-once (the database grants no general UPDATE).
 */
@Getter
@Entity
@Immutable
@Table(name = "stored_document")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StoredDocument {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "owner_type", nullable = false, length = 30)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "encryption_key_version", nullable = false)
    private short encryptionKeyVersion;

    @Column(name = "scan_status", nullable = false, length = 20)
    private String scanStatus;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private Instant uploadedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public StoredDocument(UUID id, UUID tenantId, String ownerType, UUID ownerId, String fileName, String contentType,
                          long sizeBytes, String sha256, String storageKey, short encryptionKeyVersion,
                          String scanStatus, UUID uploadedBy, Instant uploadedAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.ownerType = ownerType;
        this.ownerId = ownerId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storageKey = storageKey;
        this.encryptionKeyVersion = encryptionKeyVersion;
        this.scanStatus = scanStatus;
        this.uploadedBy = uploadedBy;
        this.uploadedAt = uploadedAt;
    }
}
