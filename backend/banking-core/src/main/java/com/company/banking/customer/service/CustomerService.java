package com.company.banking.customer.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.sequence.CheckDigits;
import com.company.banking.common.sequence.SequenceService;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.BusinessDetails;
import com.company.banking.customer.dto.ChangeCustomerStatusRequest;
import com.company.banking.customer.dto.CreateCustomerRequest;
import com.company.banking.customer.dto.CustomerResponse;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.IndividualDetails;
import com.company.banking.customer.dto.UpdateCustomerRequest;
import com.company.banking.customer.entity.BusinessProfile;
import com.company.banking.customer.entity.Customer;
import com.company.banking.customer.entity.CustomerStatus;
import com.company.banking.customer.entity.CustomerType;
import com.company.banking.customer.entity.IndividualProfile;
import com.company.banking.customer.entity.OnboardingChannel;
import com.company.banking.customer.exception.CustomerErrorCode;
import com.company.banking.customer.mapper.CustomerMapper;
import com.company.banking.customer.repository.BusinessProfileRepository;
import com.company.banking.customer.repository.CustomerRepository;
import com.company.banking.customer.repository.CustomerSearchRepository;
import com.company.banking.customer.repository.IndividualProfileRepository;
import com.company.banking.staff.service.StaffService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Customer registration, search, profile maintenance and relationship status.
 */
@Service
@RequiredArgsConstructor
public class CustomerService {

    static final String RESOURCE = "CUSTOMER";
    private static final String CUSTOMER_NUMBER_SEQUENCE = "CUSTOMER";
    private static final String TAX_ID = "tax_id";

    private final CustomerRepository customerRepository;
    private final CustomerSearchRepository searchRepository;
    private final IndividualProfileRepository individualProfileRepository;
    private final BusinessProfileRepository businessProfileRepository;
    private final CustomerAccessGuard accessGuard;
    private final CustomerViewAssembler viewAssembler;
    private final CustomerMapper mapper;
    private final SensitiveValueProtector protector;
    private final SequenceService sequenceService;
    private final BranchService branchService;
    private final StaffService staffService;
    private final AuditService auditService;
    private final ObjectProvider<CustomerHoldingsCheck> holdingsChecks;
    private final Clock clock;

    /**
     * Finds matching ids with the index-assisted search function, then loads the rows through the RLS-protected
     * repository (which also re-checks the tenant).
     */
    @Transactional(readOnly = true)
    public PageResponse<CustomerSummary> search(CustomerSearchCriteria criteria, PageRequest page) {
        BranchScope scope = CurrentActor.require().branchScope();
        if (!scope.allBranches() && scope.branchIds().isEmpty()) {
            return PageResponse.of(List.of(), page.getPageNumber(), page.getPageSize(), 0);
        }
        String term = criteria.query() == null || criteria.query().isBlank() ? null : criteria.query().trim();
        String lower = term == null ? null : term.toLowerCase(Locale.ROOT);
        String phoneDigits = term == null ? "" : term.replaceAll("[\\s-]", "");
        CustomerSearchRepository.Query query = new CustomerSearchRepository.Query(
                lower == null ? null : "%" + escapeLike(lower) + "%",
                term,
                phoneDigits.matches("^\\+?[0-9]{3,15}$") ? "%" + escapeLike(phoneDigits) + "%" : null,
                lower != null && lower.contains("@") ? lower : null,
                scope.allBranches() ? null : scope.branchIds(),
                criteria.branchId(), criteria.status(), criteria.kycStatus(), criteria.customerType(),
                page.getPageSize(), (int) page.getOffset());
        CustomerSearchRepository.Result result = searchRepository.search(query);
        long total = result.total();
        if (result.ids().isEmpty() && page.getOffset() > 0) {
            total = searchRepository.search(new CustomerSearchRepository.Query(query.namePattern(), query.exact(),
                    query.phonePattern(), query.email(), query.branchIds(), query.branchId(), query.status(),
                    query.kycStatus(), query.customerType(), 1, 0)).total();
        }
        Map<UUID, Customer> loaded = customerRepository.findAllById(result.ids()).stream()
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
        List<CustomerSummary> items = result.ids().stream()
                .map(loaded::get)
                .filter(Objects::nonNull)
                .map(mapper::toSummary)
                .toList();
        return PageResponse.of(items, page.getPageNumber(), page.getPageSize(), total);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(UUID customerId) {
        return viewAssembler.toResponse(accessGuard.loadForRead(customerId));
    }

    /**
     * Summary of a customer in the caller's branch scope, for other modules (404 outside the scope).
     */
    @Transactional(readOnly = true)
    public CustomerSummary getSummary(UUID customerId) {
        return mapper.toSummary(accessGuard.loadForRead(customerId));
    }

    /**
     * A customer about to become an account holder: branch scope is checked and the row stays locked until the
     * caller's transaction ends, so the customer cannot be closed or frozen half-way through opening the account.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerSummary lockForNewHolding(UUID customerId) {
        return mapper.toSummary(accessGuard.lockInScope(customerId));
    }

    /**
     * A customer looked up by number for the customer channel (activation of digital banking), with no branch scope:
     * the caller is the customer, not staff.
     */
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findByNumberForChannel(String customerNumber) {
        return customerRepository.findByTenantIdAndCustomerNumber(TenantContext.requireTenantId(),
                customerNumber.trim().toUpperCase(Locale.ROOT)).map(mapper::toSummary);
    }

    /**
     * The signed-in customer's own summary, for the customer channel (no staff branch scope).
     */
    @Transactional(readOnly = true)
    public Optional<CustomerSummary> findForChannel(UUID customerId) {
        return customerRepository.findByTenantIdAndId(TenantContext.requireTenantId(), customerId)
                .map(mapper::toSummary);
    }

    /**
     * Summaries of customers the caller already reached through another record (e.g. an account's holders).
     */
    @Transactional(readOnly = true)
    public Map<UUID, CustomerSummary> summaries(Collection<UUID> customerIds) {
        UUID tenantId = TenantContext.requireTenantId();
        return customerRepository.findAllById(customerIds).stream()
                .filter(customer -> customer.getTenantId().equals(tenantId))
                .map(mapper::toSummary)
                .collect(Collectors.toMap(CustomerSummary::id, Function.identity()));
    }

    @Transactional
    public CustomerResponse create(CreateCustomerRequest request) {
        CustomerType type = CustomerType.valueOf(request.customerType());
        requireMatchingProfile(type, request.individual(), request.business());
        requireBranchInScope(request.homeBranchId());
        requireRelationshipOfficer(request.relationshipOfficerId());

        UUID tenantId = TenantContext.requireTenantId();
        UUID customerId = UuidV7.next();
        Instant now = clock.instant();
        if (type == CustomerType.BUSINESS && businessProfileRepository.registrationNumberTaken(tenantId,
                request.business().registrationNumber().trim(), customerId)) {
            throw new BankingException(CustomerErrorCode.DUPLICATE_BUSINESS_REGISTRATION);
        }

        String customerNumber = CheckDigits.appendLuhn(
                String.format("%09d", sequenceService.next(CUSTOMER_NUMBER_SEQUENCE)));
        OnboardingChannel channel = request.onboardingChannel() == null
                ? OnboardingChannel.BRANCH
                : OnboardingChannel.valueOf(request.onboardingChannel());
        String displayName = type == CustomerType.INDIVIDUAL
                ? fullName(request.individual())
                : request.business().registeredName().trim();
        Customer customer = new Customer(customerId, tenantId, customerNumber, type, displayName,
                request.homeBranchId(), channel, now);
        customer.updateContact(trimToNull(request.primaryPhone()), normalizeEmail(request.email()),
                request.preferredLanguage(), request.relationshipOfficerId());
        customerRepository.saveAndFlush(customer);

        if (type == CustomerType.INDIVIDUAL) {
            IndividualProfile profile = new IndividualProfile(customerId, tenantId);
            applyIndividual(profile, request.individual());
            individualProfileRepository.save(profile);
        } else {
            BusinessProfile profile = new BusinessProfile(customerId, tenantId);
            applyBusiness(profile, request.business());
            businessProfileRepository.save(profile);
        }

        CustomerResponse created = viewAssembler.toResponse(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_CREATED", RESOURCE)
                .resourceId(customerId)
                .resourceReference(customerNumber)
                .branchId(customer.getHomeBranchId())
                .after(auditSnapshot(created))
                .build());
        return created;
    }

    @Transactional
    public CustomerResponse update(UUID customerId, UpdateCustomerRequest request) {
        Customer customer = accessGuard.lockForCapture(customerId);
        ConcurrentModificationException.assertVersion(request.version(), customer.getVersion());
        requireMatchingProfile(customer.getCustomerType(), request.individual(), request.business());
        if (!Objects.equals(request.relationshipOfficerId(), customer.getRelationshipOfficerId())) {
            requireRelationshipOfficer(request.relationshipOfficerId());
        }
        CustomerResponse before = viewAssembler.toResponse(customer);

        customer.updateContact(trimToNull(request.primaryPhone()), normalizeEmail(request.email()),
                request.preferredLanguage(), request.relationshipOfficerId());
        String displayName;
        if (customer.getCustomerType() == CustomerType.INDIVIDUAL) {
            IndividualProfile profile = individualProfileRepository
                    .findByTenantIdAndCustomerId(customer.getTenantId(), customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile"));
            if (identityChanged(profile, request.individual())) {
                CustomerAccessGuard.assertIdentityEditable(customer);
            }
            applyIndividual(profile, request.individual());
            individualProfileRepository.save(profile);
            displayName = fullName(request.individual());
        } else {
            BusinessProfile profile = businessProfileRepository
                    .findByTenantIdAndCustomerId(customer.getTenantId(), customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile"));
            if (identityChanged(profile, request.business())) {
                CustomerAccessGuard.assertIdentityEditable(customer);
                if (businessProfileRepository.registrationNumberTaken(customer.getTenantId(),
                        request.business().registrationNumber().trim(), customerId)) {
                    throw new BankingException(CustomerErrorCode.DUPLICATE_BUSINESS_REGISTRATION);
                }
            }
            applyBusiness(profile, request.business());
            businessProfileRepository.save(profile);
            displayName = request.business().registeredName().trim();
        }
        customer.markProfileUpdated(displayName, clock.instant());
        customerRepository.saveAndFlush(customer);

        CustomerResponse after = viewAssembler.toResponse(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_UPDATED", RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .before(auditSnapshot(before))
                .after(auditSnapshot(after))
                .build());
        return after;
    }

    @Transactional
    public CustomerResponse changeStatus(UUID customerId, ChangeCustomerStatusRequest request) {
        Customer customer = accessGuard.lockInScope(customerId);
        ConcurrentModificationException.assertVersion(request.version(), customer.getVersion());
        CustomerStatus previous = customer.getStatus();
        CustomerStatus target = CustomerStatus.valueOf(request.status());
        if (target == CustomerStatus.CLOSED
                && holdingsChecks.orderedStream().anyMatch(check -> check.hasOpenHoldings(customerId))) {
            throw new BankingException(CustomerErrorCode.CUSTOMER_HAS_OPEN_HOLDINGS);
        }
        customer.changeStatus(target, request.reason().trim());
        customerRepository.saveAndFlush(customer);
        auditService.record(AuditEvent.builder("CUSTOMER_STATUS_CHANGED", RESOURCE)
                .resourceId(customerId)
                .resourceReference(customer.getCustomerNumber())
                .branchId(customer.getHomeBranchId())
                .before(Map.of("status", previous))
                .after(Map.of("status", customer.getStatus()))
                .metadata("reason", request.reason().trim())
                .build());
        return viewAssembler.toResponse(customer);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void applyIndividual(IndividualProfile profile, IndividualDetails details) {
        profile.setTitle(details.title());
        profile.setFirstName(details.firstName().trim());
        profile.setMiddleName(trimToNull(details.middleName()));
        profile.setLastName(details.lastName().trim());
        profile.setDateOfBirth(details.dateOfBirth());
        profile.setGender(details.gender());
        profile.setNationality(details.nationality());
        profile.setMaritalStatus(details.maritalStatus());
        profile.setOccupation(trimToNull(details.occupation()));
        profile.setEmployerName(trimToNull(details.employerName()));
        profile.setEmploymentStatus(details.employmentStatus());
        profile.setMonthlyIncomeBand(details.monthlyIncomeBand());
        if (details.taxId() != null && !details.taxId().isBlank()) {
            SensitiveValueProtector.Protected taxId = protector.protect(profile.getTenantId(), "individual_profile",
                    profile.getCustomerId(), TAX_ID, details.taxId());
            profile.setTaxId(taxId.encrypted(), taxId.blindIndex(), taxId.masked());
        }
    }

    private void applyBusiness(BusinessProfile profile, BusinessDetails details) {
        profile.setRegisteredName(details.registeredName().trim());
        profile.setTradingName(trimToNull(details.tradingName()));
        profile.setRegistrationNumber(details.registrationNumber().trim().toUpperCase(Locale.ROOT));
        profile.setRegistrationDate(details.registrationDate());
        profile.setBusinessType(details.businessType());
        profile.setIndustrySector(trimToNull(details.industrySector()));
        profile.setAnnualTurnoverBand(details.annualTurnoverBand());
        profile.setNumberOfEmployees(details.numberOfEmployees());
        if (details.taxId() != null && !details.taxId().isBlank()) {
            SensitiveValueProtector.Protected taxId = protector.protect(profile.getTenantId(), "business_profile",
                    profile.getCustomerId(), TAX_ID, details.taxId());
            profile.setTaxId(taxId.encrypted(), taxId.blindIndex(), taxId.masked());
        }
    }

    /**
     * Legal identity of a person: anything an identity document attests.
     */
    private boolean identityChanged(IndividualProfile profile, IndividualDetails details) {
        return !Objects.equals(profile.getFirstName(), details.firstName().trim())
                || !Objects.equals(profile.getMiddleName(), trimToNull(details.middleName()))
                || !Objects.equals(profile.getLastName(), details.lastName().trim())
                || !Objects.equals(profile.getDateOfBirth(), details.dateOfBirth())
                || !Objects.equals(profile.getNationality(), details.nationality())
                || !Objects.equals(profile.getGender(), details.gender())
                || taxIdChanged(profile.getTenantId(), profile.getTaxIdBlindIndex(), details.taxId());
    }

    private boolean identityChanged(BusinessProfile profile, BusinessDetails details) {
        return !Objects.equals(profile.getRegisteredName(), details.registeredName().trim())
                || !profile.getRegistrationNumber().equalsIgnoreCase(details.registrationNumber().trim())
                || !Objects.equals(profile.getRegistrationDate(), details.registrationDate())
                || !Objects.equals(profile.getBusinessType(), details.businessType())
                || taxIdChanged(profile.getTenantId(), profile.getTaxIdBlindIndex(), details.taxId());
    }

    private boolean taxIdChanged(UUID tenantId, String currentBlindIndex, String requested) {
        return requested != null && !requested.isBlank()
                && !protector.blindIndex(tenantId, TAX_ID, requested).equals(currentBlindIndex);
    }

    private static void requireMatchingProfile(CustomerType type, IndividualDetails individual,
                                               BusinessDetails business) {
        boolean matches = type == CustomerType.INDIVIDUAL
                ? individual != null && business == null
                : business != null && individual == null;
        if (!matches) {
            throw new BankingException(CustomerErrorCode.CUSTOMER_PROFILE_MISMATCH);
        }
    }

    private void requireBranchInScope(UUID branchId) {
        if (!CurrentActor.require().branchScope().permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        BranchResponse branch = branchService.getForInternalUse(branchId);
        if (!branch.isActive()) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                    "Customers can only be registered at an active branch.");
        }
    }

    private void requireRelationshipOfficer(UUID staffId) {
        if (staffId != null && !staffService.isActiveStaff(staffId)) {
            throw new ResourceNotFoundException("Relationship officer");
        }
    }

    private static String fullName(IndividualDetails details) {
        String middle = trimToNull(details.middleName());
        return middle == null
                ? details.firstName().trim() + " " + details.lastName().trim()
                : details.firstName().trim() + " " + middle + " " + details.lastName().trim();
    }

    /**
     * Audit snapshots carry contact and profile data (needed to investigate account-takeover patterns such as
     * phone changes) but identity numbers only in masked form, and no documents.
     */
    private static Map<String, Object> auditSnapshot(CustomerResponse customer) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("customerNumber", customer.customerNumber());
        snapshot.put("displayName", customer.displayName());
        snapshot.put("primaryPhone", customer.primaryPhone());
        snapshot.put("email", customer.email());
        snapshot.put("relationshipOfficerId", customer.relationshipOfficerId());
        snapshot.put("individual", customer.individual());
        snapshot.put("business", customer.business());
        snapshot.values().removeIf(Objects::isNull);
        return snapshot;
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    public record CustomerSearchCriteria(String query, UUID branchId, String status, String kycStatus,
                                         String customerType) {
    }
}
