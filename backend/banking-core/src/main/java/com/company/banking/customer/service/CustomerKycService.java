package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.customer.dto.CustomerKycSnapshot;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.IdentityVerificationSubject;
import com.company.banking.customer.entity.BusinessProfile;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerAddress;
import com.company.banking.customer.entity.CustomerDocument;
import com.company.banking.customer.entity.CustomerIdentification;
import com.company.banking.customer.entity.CustomerStatus;
import com.company.banking.customer.entity.CustomerType;
import com.company.banking.customer.entity.IndividualProfile;
import com.company.banking.customer.entity.RelatedParty;
import com.company.banking.customer.entity.RiskLevel;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.BusinessProfileRepository;
import com.company.banking.customer.repository.CustomerAddressRepository;
import com.company.banking.customer.repository.CustomerDocumentRepository;
import com.company.banking.customer.repository.CustomerIdentificationRepository;
import com.company.banking.customer.repository.CustomerRepository;
import com.company.banking.customer.repository.IndividualProfileRepository;
import com.company.banking.customer.repository.NextOfKinRepository;
import com.company.banking.customer.repository.RelatedPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The customer module's API for the KYC module: read the facts KYC rules need and apply KYC outcomes to the
 * customer. Access rules (scope, capture permission, row locks) are the same as for direct customer changes.
 */
@Service
@RequiredArgsConstructor
public class CustomerKycService {

    private final CustomerRepository customerRepository;
    private final IndividualProfileRepository individualProfileRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerIdentificationRepository identificationRepository;
    private final NextOfKinRepository nextOfKinRepository;
    private final RelatedPartyRepository relatedPartyRepository;
    private final CustomerDocumentRepository documentRepository;
    private final CustomerAccessGuard accessGuard;
    private final IdentificationTypeService identificationTypeService;
    private final SensitiveValueProtector protector;
    private final CustomerMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CustomerKycSnapshot snapshot(UUID customerId) {
        return snapshotOf(accessGuard.loadForRead(customerId));
    }

    /**
     * Summaries for listing KYC cases (same scope rules as a customer search).
     */
    @Transactional(readOnly = true)
    public Map<UUID, CustomerSummary> summaries(Collection<UUID> customerIds) {
        return customerRepository.findAllById(customerIds).stream()
                .map(mapper::toSummary)
                .collect(Collectors.toMap(CustomerSummary::id, Function.identity()));
    }

    /**
     * The primary identification with the plaintext number, for an identity-verification provider. In memory only.
     */
    @Transactional(readOnly = true)
    public IdentityVerificationSubject identityVerificationSubject(UUID customerId) {
        Customer customer = accessGuard.loadForRead(customerId);
        CustomerIdentification identification = primaryIdentification(customer)
                .orElseThrow(() -> new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                        "The customer has no identification to verify."));
        String number = protector.reveal(customer.getTenantId(), CustomerIdentityService.TABLE,
                identification.getId(), CustomerIdentityService.purpose(identification.getIdTypeCode()),
                identification.getIdNumberEncrypted());
        if (customer.getCustomerType() == CustomerType.INDIVIDUAL) {
            IndividualProfile profile = individualProfileRepository
                    .findByTenantIdAndCustomerId(customer.getTenantId(), customerId).orElseThrow();
            return new IdentityVerificationSubject(identification.getId(), identification.getIdTypeCode(), number,
                    identification.getIssuingCountry(), profile.getFirstName(), profile.getMiddleName(),
                    profile.getLastName(), profile.getDateOfBirth(), null);
        }
        BusinessProfile profile = businessProfileRepository
                .findByTenantIdAndCustomerId(customer.getTenantId(), customerId).orElseThrow();
        return new IdentityVerificationSubject(identification.getId(), identification.getIdTypeCode(), number,
                identification.getIssuingCountry(), null, null, null, null, profile.getRegisteredName());
    }

    @Transactional
    public void recordIdentificationVerification(UUID customerId, UUID identificationId, boolean passed,
                                                 String reference) {
        Customer customer = accessGuard.lockInScope(customerId);
        identificationRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customerId,
                        identificationId)
                .ifPresent(identification -> {
                    identification.recordVerification(passed, reference, clock.instant());
                    identificationRepository.saveAndFlush(identification);
                });
    }

    @Transactional
    public CustomerKycSnapshot kycStarted(UUID customerId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        customer.kycStarted();
        return snapshotOf(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public CustomerKycSnapshot kycSubmitted(UUID customerId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        customer.kycSubmitted();
        return snapshotOf(customerRepository.saveAndFlush(customer));
    }

    @Transactional
    public void kycCancelled(UUID customerId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        customer.kycCancelled();
        customerRepository.saveAndFlush(customer);
    }

    @Transactional
    public void kycReturned(UUID customerId) {
        Customer customer = accessGuard.lockInScope(customerId);
        customer.kycReturned();
        customerRepository.saveAndFlush(customer);
    }

    @Transactional
    public void kycRejected(UUID customerId) {
        Customer customer = accessGuard.lockInScope(customerId);
        customer.kycRejected();
        customerRepository.saveAndFlush(customer);
    }

    /**
     * Verifies the customer at the given tier and risk level; a customer still in onboarding becomes active.
     */
    @Transactional
    public void kycApproved(UUID customerId, String tierCode, String riskLevel) {
        Customer customer = accessGuard.lockInScope(customerId);
        CustomerStatus previousStatus = customer.getStatus();
        customer.kycApproved(tierCode, RiskLevel.valueOf(riskLevel), clock.instant());
        customerRepository.saveAndFlush(customer);
        if (previousStatus != customer.getStatus()) {
            auditService.record(AuditEvent.builder("CUSTOMER_STATUS_CHANGED", CustomerService.RESOURCE)
                    .resourceId(customerId)
                    .resourceReference(customer.getCustomerNumber())
                    .branchId(customer.getHomeBranchId())
                    .before(Map.of("status", previousStatus))
                    .after(Map.of("status", customer.getStatus()))
                    .metadata("reason", "KYC approved")
                    .build());
        }
    }

    private CustomerKycSnapshot snapshotOf(Customer customer) {
        UUID tenantId = customer.getTenantId();
        UUID id = customer.getId();
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        List<CustomerIdentification> identifications =
                identificationRepository.findByTenantIdAndCustomerIdAndActiveTrue(tenantId, id);
        CustomerKycSnapshot.PrimaryIdentification primary = primaryIdentification(customer)
                .map(identification -> new CustomerKycSnapshot.PrimaryIdentification(identification.getId(),
                        identification.getIdTypeCode(),
                        identificationTypeService.supportsElectronicVerification(identification.getIdTypeCode()),
                        identification.getVerificationStatus(), identification.isExpired(today)))
                .orElse(null);
        Map<String, List<String>> documents = documentRepository
                .findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id).stream()
                .collect(Collectors.groupingBy(CustomerDocument::getDocumentType,
                        Collectors.mapping(CustomerDocument::getReviewStatus, Collectors.toList())));
        boolean isIndividual = customer.getCustomerType() == CustomerType.INDIVIDUAL;
        boolean hasEmploymentInfo = isIndividual
                ? individualProfileRepository.findByTenantIdAndCustomerId(tenantId, id)
                .map(profile -> profile.getEmploymentStatus() != null).orElse(false)
                : businessProfileRepository.findByTenantIdAndCustomerId(tenantId, id)
                .map(profile -> profile.getIndustrySector() != null).orElse(false);
        boolean hasRelatedParties = relatedPartyRepository.findByTenantIdAndCustomerIdOrderByCreatedAt(tenantId, id)
                .stream().anyMatch(RelatedParty::isActive);
        return new CustomerKycSnapshot(id, customer.getCustomerNumber(), customer.getCustomerType().name(),
                customer.getStatus().name(), customer.getKycStatus().name(), customer.getKycTierCode(),
                customer.getHomeBranchId(),
                customer.getDisplayName(), !identifications.isEmpty(), primary,
                addressRepository.findByTenantIdAndCustomerIdAndActiveTrue(tenantId, id).stream()
                        .anyMatch(CustomerAddress::isActive),
                documents,
                nextOfKinRepository.existsByTenantIdAndCustomerIdAndActiveTrue(tenantId, id),
                hasEmploymentInfo,
                hasRelatedParties);
    }

    private Optional<CustomerIdentification> primaryIdentification(Customer customer) {
        return identificationRepository.findByTenantIdAndCustomerIdAndActiveTrue(customer.getTenantId(),
                        customer.getId()).stream()
                .filter(CustomerIdentification::isPrimary)
                .findFirst();
    }
}
