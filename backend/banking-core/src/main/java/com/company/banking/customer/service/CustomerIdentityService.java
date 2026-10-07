package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.customer.dto.IdentificationRequest;
import com.company.banking.customer.dto.IdentificationResponse;
import com.company.banking.customer.dto.RevealedIdentification;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerIdentification;
import com.company.banking.customer.entity.IdentificationType;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.CustomerIdentificationRepository;
import com.company.banking.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Identity documents of customers. Numbers are validated against the institution's configured format, checked for
 * duplicates through the blind index, and stored encrypted; reading a full number is a separately audited action.
 */
@Service
@RequiredArgsConstructor
public class CustomerIdentityService {

    static final String TABLE = "customer_identification";
    static final String PURPOSE = "id_number";

    private final CustomerRepository customerRepository;
    private final CustomerIdentificationRepository identificationRepository;
    private final CustomerAccessGuard accessGuard;
    private final IdentificationTypeService identificationTypeService;
    private final SensitiveValueProtector protector;
    private final CustomerMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public IdentificationResponse add(UUID customerId, IdentificationRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        CustomerAccessGuard.assertIdentityEditable(customer);
        IdentificationType type = identificationTypeService.requireUsable(request.idTypeCode(),
                customer.getCustomerType());
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (type.isRequiresExpiry() && request.expiryDate() == null) {
            throw new BankingException(CustomerErrorCode.IDENTIFICATION_EXPIRY_REQUIRED);
        }
        if (request.expiryDate() != null && !request.expiryDate().isAfter(today)) {
            throw new BankingException(CustomerErrorCode.IDENTIFICATION_EXPIRED);
        }

        UUID identificationId = UuidV7.next();
        SensitiveValueProtector.Protected number = protector.protect(customer.getTenantId(), TABLE, identificationId,
                purpose(type.getCode()), request.idNumber());
        if (!type.matchesFormat(number.normalized())) {
            throw new BankingException(CustomerErrorCode.INVALID_IDENTIFICATION_NUMBER,
                    type.getFormatHint() == null
                            ? CustomerErrorCode.INVALID_IDENTIFICATION_NUMBER.defaultMessage()
                            : "The number must look like " + type.getFormatHint() + ".");
        }
        identificationRepository.findByTenantIdAndIdTypeCodeAndIdNumberBlindIndexAndActiveTrue(
                        customer.getTenantId(), type.getCode(), number.blindIndex())
                .ifPresent(existing -> {
                    String owner = customerRepository.findByTenantIdAndId(customer.getTenantId(),
                                    existing.getCustomerId())
                            .map(Customer::getCustomerNumber).orElse("another customer");
                    throw new BankingException(CustomerErrorCode.DUPLICATE_IDENTIFICATION,
                            "This identity document is already registered to customer " + owner + ".");
                });

        CustomerIdentification identification = new CustomerIdentification(identificationId, customer.getTenantId(),
                customerId, type.getCode(), number.encrypted(), number.blindIndex(), number.masked(),
                request.issuingCountry(), request.issueDate(), request.expiryDate());
        boolean first = identificationRepository.findByTenantIdAndCustomerIdAndActiveTrue(customer.getTenantId(),
                customerId).isEmpty();
        if (first || request.primary()) {
            identificationRepository.findByTenantIdAndCustomerIdAndActiveTrue(customer.getTenantId(), customerId)
                    .stream().filter(CustomerIdentification::isPrimary).forEach(other -> {
                        other.setPrimary(false);
                        identificationRepository.saveAndFlush(other);
                    });
            identification.setPrimary(true);
        }
        IdentificationResponse created = mapper.toResponse(identificationRepository.saveAndFlush(identification));
        customer.markProfileUpdated(customer.getDisplayName(), clock.instant());
        customerRepository.saveAndFlush(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_IDENTIFICATION_ADDED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .after(created)
                .build());
        return created;
    }

    @Transactional
    public void remove(UUID customerId, UUID identificationId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        CustomerAccessGuard.assertIdentityEditable(customer);
        CustomerIdentification identification = identificationRepository
                .findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customerId, identificationId)
                .filter(CustomerIdentification::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Identification"));
        IdentificationResponse before = mapper.toResponse(identification);
        identification.deactivate();
        identificationRepository.saveAndFlush(identification);
        customer.markProfileUpdated(customer.getDisplayName(), clock.instant());
        customerRepository.saveAndFlush(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_IDENTIFICATION_REMOVED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .before(before)
                .build());
    }

    /**
     * Returns the full number. Recorded in the audit trail (without the number itself).
     */
    @Transactional
    public RevealedIdentification reveal(UUID customerId, UUID identificationId) {
        Customer customer = accessGuard.loadForRead(customerId);
        CustomerIdentification identification = identificationRepository
                .findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customerId, identificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Identification"));
        String number = protector.reveal(customer.getTenantId(), TABLE, identification.getId(),
                purpose(identification.getIdTypeCode()), identification.getIdNumberEncrypted());
        auditService.record(AuditEvent.builder("CUSTOMER_IDENTIFICATION_REVEALED", CustomerService.RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .metadata("identificationId", identification.getId())
                .metadata("idTypeCode", identification.getIdTypeCode())
                .build());
        return new RevealedIdentification(identification.getId(), identification.getIdTypeCode(), number);
    }

    static String purpose(String idTypeCode) {
        return PURPOSE + ":" + idTypeCode;
    }
}
