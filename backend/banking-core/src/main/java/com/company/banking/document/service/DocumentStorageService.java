package com.company.banking.document.service;

import com.company.banking.common.crypto.FieldEncryptionService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.document.dto.DocumentContent;
import com.company.banking.document.dto.StoredDocumentInfo;
import com.company.banking.document.entity.StoredDocument;
import com.company.banking.document.exception.DocumentErrorCode;
import com.company.banking.document.repository.StoredDocumentRepository;
import com.company.banking.document.storage.DocumentScanner;
import com.company.banking.document.storage.ObjectStorage;
import com.company.banking.document.storage.StorageProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Stores and retrieves documents (KYC images, PDFs). Content is type-checked by magic bytes, hashed, scanned,
 * encrypted with a key bound to the tenant and document id, and written to object storage under a generated key.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private final StoredDocumentRepository repository;
    private final ObjectStorage objectStorage;
    private final DocumentScanner scanner;
    private final FieldEncryptionService encryption;
    private final StorageProperties properties;
    private final Clock clock;

    /**
     * Must run inside the owning business transaction, so the metadata row commits with the business record.
     * If that transaction rolls back, the encrypted object is left orphaned (unreadable without metadata) and is
     * removed by the storage cleanup job.
     */
    @Transactional
    public StoredDocumentInfo store(String ownerType, UUID ownerId, String originalFileName, byte[] content) {
        if (content == null || content.length == 0) {
            throw new BankingException(DocumentErrorCode.DOCUMENT_EMPTY);
        }
        if (content.length > properties.maxDocumentSize().toBytes()) {
            throw new BankingException(DocumentErrorCode.DOCUMENT_TOO_LARGE);
        }
        String contentType = ContentTypeSniffer.sniff(content)
                .orElseThrow(() -> new BankingException(DocumentErrorCode.UNSUPPORTED_DOCUMENT_TYPE));
        DocumentScanner.ScanResult scan = scanner.scan(content);
        if (scan == DocumentScanner.ScanResult.INFECTED) {
            log.warn("Upload rejected by malware scanner for owner type {}", ownerType);
            throw new BankingException(DocumentErrorCode.DOCUMENT_REJECTED_BY_SCANNER);
        }

        UUID tenantId = TenantContext.requireTenantId();
        UUID documentId = UuidV7.next();
        Instant now = clock.instant();
        String storageKey = tenantId + "/" + now.atOffset(ZoneOffset.UTC).getYear() + "/" + documentId;
        objectStorage.put(storageKey, encryption.encryptBytes(content, context(tenantId, documentId)));

        StoredDocument document = repository.saveAndFlush(new StoredDocument(documentId, tenantId, ownerType,
                ownerId, sanitizeFileName(originalFileName, contentType), contentType, content.length,
                sha256(content), storageKey, (short) encryption.activeKeyVersion(), scan.name(),
                CurrentActor.currentActorId().orElse(null), now));
        return toInfo(document);
    }

    @Transactional(readOnly = true)
    public StoredDocumentInfo info(UUID documentId) {
        return toInfo(load(documentId));
    }

    @Transactional(readOnly = true)
    public DocumentContent read(UUID documentId) {
        StoredDocument document = load(documentId);
        byte[] plaintext = encryption.decryptBytes(objectStorage.get(document.getStorageKey()),
                context(document.getTenantId(), document.getId()));
        if (!sha256(plaintext).equals(document.getSha256())) {
            log.error("Integrity check failed for stored document {}", document.getId());
            throw new BankingException(DocumentErrorCode.DOCUMENT_INTEGRITY_FAILURE);
        }
        return new DocumentContent(toInfo(document), plaintext);
    }

    private StoredDocument load(UUID documentId) {
        return repository.findByTenantIdAndId(TenantContext.requireTenantId(), documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document"));
    }

    private static StoredDocumentInfo toInfo(StoredDocument document) {
        return new StoredDocumentInfo(document.getId(), document.getFileName(), document.getContentType(),
                document.getSizeBytes(), document.getSha256(), document.getScanStatus(), document.getUploadedBy(),
                document.getUploadedAt());
    }

    private static String context(UUID tenantId, UUID documentId) {
        return tenantId + "/stored_document/" + documentId;
    }

    /**
     * Keeps a readable, harmless file name for downloads; the extension follows the sniffed type.
     */
    static String sanitizeFileName(String original, String contentType) {
        String base = original == null ? "document" : original.replaceAll("^.*[/\\\\]", "");
        base = base.replaceAll("\\.[A-Za-z0-9]{1,5}$", "").replaceAll("[^A-Za-z0-9 _-]", "_").trim();
        if (base.isEmpty()) {
            base = "document";
        }
        if (base.length() > 100) {
            base = base.substring(0, 100);
        }
        String extension = switch (contentType) {
            case ContentTypeSniffer.JPEG -> ".jpg";
            case ContentTypeSniffer.PNG -> ".png";
            default -> ".pdf";
        };
        return base + extension;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
