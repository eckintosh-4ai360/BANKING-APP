package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.customer.dto.AddressRequest;
import com.company.banking.customer.dto.AddressResponse;
import com.company.banking.customer.dto.NextOfKinRequest;
import com.company.banking.customer.dto.NextOfKinResponse;
import com.company.banking.customer.dto.RelatedPartyRequest;
import com.company.banking.customer.dto.RelatedPartyResponse;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerAddress;
import com.company.banking.customer.entity.CustomerType;
import com.company.banking.customer.entity.NextOfKin;
import com.company.banking.customer.entity.RelatedParty;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.CustomerAddressRepository;
import com.company.banking.customer.repository.CustomerRepository;
import com.company.banking.customer.repository.NextOfKinRepository;
import com.company.banking.customer.repository.RelatedPartyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Addresses, next of kin and (for businesses) related parties. Every change bumps the customer's version so the
 * customer record shows it changed.
 */
@Service
@RequiredArgsConstructor
public class CustomerContactService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final NextOfKinRepository nextOfKinRepository;
    private final RelatedPartyRepository relatedPartyRepository;
    private final CustomerAccessGuard accessGuard;
    private final IdentificationTypeService identificationTypeService;
    private final SensitiveValueProtector protector;
    private final CustomerMapper mapper;
    private final AuditService auditService;
    private final Clock clock;

    // ------------------------------------------------------------------------------------------------ addresses

    @Transactional
    public AddressResponse addAddress(UUID customerId, AddressRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        boolean first = addressRepository.findByTenantIdAndCustomerIdAndActiveTrue(customer.getTenantId(), customerId)
                .isEmpty();
        CustomerAddress address = new CustomerAddress(UuidV7.next(), customer.getTenantId(), customerId);
        applyAddress(address, request);
        makePrimaryIf(customer, address, first || request.primary());
        AddressResponse created = mapper.toResponse(addressRepository.saveAndFlush(address));
        touch(customer);
        audit("CUSTOMER_ADDRESS_ADDED", customer, null, created);
        return created;
    }

    @Transactional
    public AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        CustomerAddress address = addressRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(),
                        customerId, addressId)
                .filter(CustomerAddress::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Address"));
        ConcurrentModificationException.assertVersion(request.version(), address.getVersion());
        AddressResponse before = mapper.toResponse(address);
        applyAddress(address, request);
        makePrimaryIf(customer, address, request.primary() || address.isPrimary());
        AddressResponse after = mapper.toResponse(addressRepository.saveAndFlush(address));
        touch(customer);
        audit("CUSTOMER_ADDRESS_UPDATED", customer, before, after);
        return after;
    }

    @Transactional
    public void removeAddress(UUID customerId, UUID addressId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        CustomerAddress address = addressRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(),
                        customerId, addressId)
                .filter(CustomerAddress::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Address"));
        AddressResponse before = mapper.toResponse(address);
        address.deactivate();
        addressRepository.saveAndFlush(address);
        touch(customer);
        audit("CUSTOMER_ADDRESS_REMOVED", customer, before, null);
    }

    // ---------------------------------------------------------------------------------------------- next of kin

    @Transactional
    public NextOfKinResponse addNextOfKin(UUID customerId, NextOfKinRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        NextOfKin nextOfKin = new NextOfKin(UuidV7.next(), customer.getTenantId(), customerId);
        nextOfKin.update(request.fullName().trim(), request.relationship().trim(), request.phone(), request.email(),
                request.address(), request.primary());
        NextOfKinResponse created = mapper.toResponse(nextOfKinRepository.saveAndFlush(nextOfKin));
        touch(customer);
        audit("CUSTOMER_NEXT_OF_KIN_ADDED", customer, null, created);
        return created;
    }

    @Transactional
    public NextOfKinResponse updateNextOfKin(UUID customerId, UUID nextOfKinId, NextOfKinRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        NextOfKin nextOfKin = activeNextOfKin(customer, nextOfKinId);
        ConcurrentModificationException.assertVersion(request.version(), nextOfKin.getVersion());
        NextOfKinResponse before = mapper.toResponse(nextOfKin);
        nextOfKin.update(request.fullName().trim(), request.relationship().trim(), request.phone(), request.email(),
                request.address(), request.primary());
        NextOfKinResponse after = mapper.toResponse(nextOfKinRepository.saveAndFlush(nextOfKin));
        touch(customer);
        audit("CUSTOMER_NEXT_OF_KIN_UPDATED", customer, before, after);
        return after;
    }

    @Transactional
    public void removeNextOfKin(UUID customerId, UUID nextOfKinId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        NextOfKin nextOfKin = activeNextOfKin(customer, nextOfKinId);
        NextOfKinResponse before = mapper.toResponse(nextOfKin);
        nextOfKin.deactivate();
        nextOfKinRepository.saveAndFlush(nextOfKin);
        touch(customer);
        audit("CUSTOMER_NEXT_OF_KIN_REMOVED", customer, before, null);
    }

    // ------------------------------------------------------------------------------------------- related parties

    /**
     * Directors, owners and signatories are part of a business customer's verified identity.
     */
    @Transactional
    public RelatedPartyResponse addRelatedParty(UUID customerId, RelatedPartyRequest request) {
        Customer customer = lockBusinessIdentity(customerId);
        RelatedParty party = new RelatedParty(UuidV7.next(), customer.getTenantId(), customerId);
        applyRelatedParty(customer, party, request);
        RelatedPartyResponse created = mapper.toResponse(relatedPartyRepository.saveAndFlush(party));
        touch(customer);
        audit("CUSTOMER_RELATED_PARTY_ADDED", customer, null, created);
        return created;
    }

    @Transactional
    public RelatedPartyResponse updateRelatedParty(UUID customerId, UUID partyId, RelatedPartyRequest request) {
        Customer customer = lockBusinessIdentity(customerId);
        RelatedParty party = activeRelatedParty(customer, partyId);
        ConcurrentModificationException.assertVersion(request.version(), party.getVersion());
        RelatedPartyResponse before = mapper.toResponse(party);
        applyRelatedParty(customer, party, request);
        RelatedPartyResponse after = mapper.toResponse(relatedPartyRepository.saveAndFlush(party));
        touch(customer);
        audit("CUSTOMER_RELATED_PARTY_UPDATED", customer, before, after);
        return after;
    }

    @Transactional
    public void removeRelatedParty(UUID customerId, UUID partyId) {
        Customer customer = lockBusinessIdentity(customerId);
        RelatedParty party = activeRelatedParty(customer, partyId);
        RelatedPartyResponse before = mapper.toResponse(party);
        party.deactivate();
        relatedPartyRepository.saveAndFlush(party);
        touch(customer);
        audit("CUSTOMER_RELATED_PARTY_REMOVED", customer, before, null);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void applyAddress(CustomerAddress address, AddressRequest request) {
        address.update(request.addressType(), request.line1().trim(), CustomerService.trimToNull(request.line2()),
                CustomerService.trimToNull(request.city()), CustomerService.trimToNull(request.district()),
                CustomerService.trimToNull(request.region()), request.countryCode(), request.digitalAddress(),
                CustomerService.trimToNull(request.landmark()));
    }

    private void makePrimaryIf(Customer customer, CustomerAddress address, boolean primary) {
        if (!primary) {
            address.setPrimary(false);
            return;
        }
        addressRepository.findByTenantIdAndCustomerIdAndActiveTrue(customer.getTenantId(), customer.getId()).stream()
                .filter(other -> other.isPrimary() && !other.getId().equals(address.getId()))
                .forEach(other -> {
                    other.setPrimary(false);
                    addressRepository.saveAndFlush(other);
                });
        address.setPrimary(true);
    }

    private void applyRelatedParty(Customer customer, RelatedParty party, RelatedPartyRequest request) {
        party.setFullName(request.fullName().trim());
        party.setPartyRole(request.role());
        party.setOwnershipPercent(request.ownershipPercent());
        party.setNationality(request.nationality());
        party.setDateOfBirth(request.dateOfBirth());
        party.setPhone(request.phone());
        party.setEmail(request.email());
        party.setPoliticallyExposed(request.politicallyExposed());
        if (request.relatedCustomerId() != null) {
            customerRepository.findByTenantIdAndId(customer.getTenantId(), request.relatedCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Related customer"));
        }
        party.setRelatedCustomerId(request.relatedCustomerId());
        boolean hasType = request.idTypeCode() != null && !request.idTypeCode().isBlank();
        boolean hasNumber = request.idNumber() != null && !request.idNumber().isBlank();
        if (hasType != hasNumber) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "Provide both the identification type and number, or neither.");
        }
        if (hasType) {
            String typeCode = identificationTypeService.requireUsable(request.idTypeCode(), null).getCode();
            SensitiveValueProtector.Protected number = protector.protect(customer.getTenantId(),
                    "customer_related_party", party.getId(), "id_number", request.idNumber());
            party.setIdentification(typeCode, number.encrypted(), number.blindIndex(), number.masked());
        }
    }

    private Customer lockBusinessIdentity(UUID customerId) {
        Customer customer = accessGuard.lockForCapture(customerId);
        if (customer.getCustomerType() != CustomerType.BUSINESS) {
            throw new BankingException(CustomerErrorCode.CUSTOMER_PROFILE_MISMATCH,
                    "Related parties apply to business customers.");
        }
        CustomerAccessGuard.assertIdentityEditable(customer);
        return customer;
    }

    private NextOfKin activeNextOfKin(Customer customer, UUID nextOfKinId) {
        return nextOfKinRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customer.getId(),
                        nextOfKinId)
                .filter(NextOfKin::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Next of kin"));
    }

    private RelatedParty activeRelatedParty(Customer customer, UUID partyId) {
        return relatedPartyRepository.findByTenantIdAndCustomerIdAndId(customer.getTenantId(), customer.getId(),
                        partyId)
                .filter(RelatedParty::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Related party"));
    }

    private void touch(Customer customer) {
        customer.markProfileUpdated(customer.getDisplayName(), clock.instant());
        customerRepository.saveAndFlush(customer);
    }

    private void audit(String action, Customer customer, Object before, Object after) {
        auditService.record(AuditEvent.builder(action, CustomerService.RESOURCE)
                .resourceId(customer.getId())
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .before(before)
                .after(after)
                .build());
    }
}
