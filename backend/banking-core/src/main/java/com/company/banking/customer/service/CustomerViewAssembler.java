package com.company.banking.customer.service;

import com.company.banking.customer.dto.CustomerDocumentResponse;
import com.company.banking.customer.dto.CustomerResponse;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerAddress;
import com.company.banking.customer.entity.CustomerDocument;
import com.company.banking.customer.entity.CustomerIdentification;
import com.company.banking.customer.entity.NextOfKin;
import com.company.banking.customer.entity.RelatedParty;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.BusinessProfileRepository;
import com.company.banking.customer.repository.CustomerAddressRepository;
import com.company.banking.customer.repository.CustomerDocumentRepository;
import com.company.banking.customer.repository.CustomerIdentificationRepository;
import com.company.banking.customer.repository.IndividualProfileRepository;
import com.company.banking.customer.repository.NextOfKinRepository;
import com.company.banking.customer.repository.RelatedPartyRepository;
import com.company.banking.document.dto.StoredDocumentInfo;
import com.company.banking.document.service.DocumentStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Builds the full customer view (profile, active contacts and identifications with masked numbers, documents).
 */
@Component
@RequiredArgsConstructor
public class CustomerViewAssembler {

    private final IndividualProfileRepository individualProfileRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerIdentificationRepository identificationRepository;
    private final NextOfKinRepository nextOfKinRepository;
    private final RelatedPartyRepository relatedPartyRepository;
    private final CustomerDocumentRepository documentRepository;
    private final DocumentStorageService documentStorageService;
    private final CustomerMapper mapper;

    public CustomerResponse toResponse(Customer customer) {
        UUID tenantId = customer.getTenantId();
        UUID id = customer.getId();
        return new CustomerResponse(
                id,
                customer.getCustomerNumber(),
                customer.getCustomerType().name(),
                customer.getStatus().name(),
                customer.getStatusReason(),
                customer.getKycStatus().name(),
                customer.getKycTierCode(),
                customer.getKycVerifiedAt(),
                customer.getRiskLevel().name(),
                customer.getDisplayName(),
                customer.getPrimaryPhone(),
                customer.getEmail(),
                customer.getPreferredLanguage(),
                customer.getHomeBranchId(),
                customer.getRelationshipOfficerId(),
                customer.getOnboardingChannel().name(),
                individualProfileRepository.findByTenantIdAndCustomerId(tenantId, id).map(mapper::toResponse)
                        .orElse(null),
                businessProfileRepository.findByTenantIdAndCustomerId(tenantId, id).map(mapper::toResponse)
                        .orElse(null),
                addressRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id).stream()
                        .filter(CustomerAddress::isActive).map(mapper::toResponse).toList(),
                identificationRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id).stream()
                        .filter(CustomerIdentification::isActive).map(mapper::toResponse).toList(),
                nextOfKinRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id).stream()
                        .filter(NextOfKin::isActive).map(mapper::toResponse).toList(),
                relatedPartyRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id).stream()
                        .filter(RelatedParty::isActive).map(mapper::toResponse).toList(),
                documents(tenantId, id),
                customer.getCreatedAt(),
                customer.getUpdatedAt(),
                customer.getVersion());
    }

    public List<CustomerDocumentResponse> documents(UUID tenantId, UUID customerId) {
        return documentRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CustomerDocumentResponse toResponse(CustomerDocument document) {
        StoredDocumentInfo info = documentStorageService.info(document.getStoredDocumentId());
        return new CustomerDocumentResponse(document.getId(), document.getDocumentType(), document.getReviewStatus(),
                document.getReviewNote(), document.getReviewedBy(), document.getReviewedAt(), info.fileName(),
                info.contentType(), info.sizeBytes(), info.scanStatus(), info.uploadedBy(), info.uploadedAt());
    }
}
