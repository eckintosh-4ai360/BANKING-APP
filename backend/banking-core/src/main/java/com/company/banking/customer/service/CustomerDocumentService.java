package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.customer.dto.CustomerDocumentResponse;
import com.company.banking.customer.dto.DocumentReviewRequest;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerDocument;
import com.company.banking.customer.repository.CustomerDocumentRepository;
import com.company.banking.customer.repository.CustomerRepository;
import com.company.banking.document.dto.DocumentContent;
import com.company.banking.document.dto.StoredDocumentInfo;
import com.company.banking.document.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * KYC documents: upload (capture), listing, audited download and four-eyes review.
 */
@Service
@RequiredArgsConstructor
public class CustomerDocumentService {

    private static final String OWNER_TYPE = "CUSTOMER";

    private final CustomerRepository customerRepository;
    private final CustomerDocumentRepository documentRepository;
    private final CustomerAccessGuard accessGuard;
    private final CustomerViewAssembler viewAssembler;
    private final DocumentStorageService documentStorageService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public CustomerDocumentResponse upload(UUID customerId, String documentType, String fileName, byte[] content) {
        Customer customer = accessGuard.lockForCapture(customerId);
        StoredDocumentInfo stored = documentStorageService.store(OWNER_TYPE, customerId, fileName, content);
        CustomerDocument document = documentRepository.saveAndFlush(new CustomerDocument(UuidV7.next(),
                customer.getTenantId(), customerId, stored.id(), documentType));
        customer.markProfileUpdated(customer.getDisplayName(), clock.instant());
        customerRepository.saveAndFlush(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_DOCUMENT_UPLOADED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .metadata("documentId", document.getId())
                .metadata("documentType", documentType)
                .metadata("contentType", stored.contentType())
                .metadata("sizeBytes", stored.sizeBytes())
                .metadata("sha256", stored.sha256())
                .build());
        return viewAssembler.toResponse(document);
    }

    @Transactional(readOnly = true)
    public List<CustomerDocumentResponse> list(UUID customerId) {
        Customer customer = accessGuard.loadForRead(customerId);
        return viewAssembler.documents(customer.getTenantId(), customerId);
    }

    /**
     * Viewing an identity document is recorded in the audit trail.
     */
    @Transactional
    public DocumentContent download(UUID customerId, UUID documentId) {
        Customer customer = accessGuard.loadForRead(customerId);
        CustomerDocument document = load(customer, documentId);
        DocumentContent content = documentStorageService.read(document.getStoredDocumentId());
        auditService.record(AuditEvent.builder("CUSTOMER_DOCUMENT_VIEWED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .metadata("documentId", documentId)
                .metadata("documentType", document.getDocumentType())
                .build());
        return content;
    }

    /**
     * The person who uploaded a document cannot be the one who accepts or rejects it.
     */
    @Transactional
    public CustomerDocumentResponse review(UUID customerId, UUID documentId, DocumentReviewRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        Customer customer = accessGuard.lockInScope(customerId);
        CustomerDocument document = load(customer, documentId);
        StoredDocumentInfo stored = documentStorageService.info(document.getStoredDocumentId());
        if (actor.id() != null && Objects.equals(actor.id(), stored.uploadedBy())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
        boolean accepted = CustomerDocument.ACCEPTED.equals(request.decision());
        if (!accepted && (request.note() == null || request.note().isBlank())) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED, "A reason is required when rejecting.");
        }
        String previous = document.getReviewStatus();
        document.review(accepted, request.note(), actor.id(), clock.instant());
        documentRepository.saveAndFlush(document);
        auditService.record(AuditEvent.builder("CUSTOMER_DOCUMENT_REVIEWED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .before(Map.of("reviewStatus", previous))
                .after(Map.of("reviewStatus", document.getReviewStatus()))
                .metadata("documentId", documentId)
                .metadata("documentType", document.getDocumentType())
                .metadata("note", request.note())
                .build());
        return viewAssembler.toResponse(document);
    }

    private CustomerDocument load(Customer customer, UUID documentId) {
        return documentRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customer.getId(),
                        documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document"));
    }
}
