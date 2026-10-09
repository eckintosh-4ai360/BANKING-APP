package com.company.banking.fieldops.service;

import com.company.banking.account.dto.AccountSummary;
import com.company.banking.account.service.AccountService;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.service.CustomerService;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.entity.CustomerAssignment;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.CustomerAssignmentRepository;
import com.company.banking.susu.dto.SusuDtos;
import com.company.banking.susu.service.SusuPlanService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Which field officer looks after which customer. An officer collects only from their own customers; reassigning
 * a customer ends the previous assignment in the same transaction.
 */
@Service
@RequiredArgsConstructor
public class CustomerAssignmentService {

    static final String RESOURCE = "CUSTOMER_ASSIGNMENT";
    /** Account statuses a collection can be credited to. */
    private static final Set<String> COLLECTABLE = Set.of("PENDING", "ACTIVE", "RESTRICTED", "DORMANT");

    private final CustomerAssignmentRepository assignments;
    private final FieldOfficerService officerService;
    private final CustomerService customerService;
    private final AccountService accountService;
    private final SusuPlanService susuPlans;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public FieldResponses.Assignment assign(OfficerRequests.Assign request) {
        UUID tenantId = TenantContext.requireTenantId();
        FieldOfficer officer = officerService.loadInScope(request.officerId());
        if (!officer.isActive()) {
            throw new BankingException(FieldErrorCode.OFFICER_SUSPENDED);
        }
        CustomerSummary customer = customerService.getSummary(request.customerId());
        if (!customer.homeBranchId().equals(officer.getBranchId())) {
            throw new BankingException(FieldErrorCode.CUSTOMER_OTHER_BRANCH);
        }
        Instant now = clock.instant();
        UUID actorId = CurrentActor.currentActorId().orElse(null);
        CustomerAssignment current = assignments.lockActiveByCustomer(tenantId, customer.id()).orElse(null);
        if (current != null) {
            if (current.getOfficerId().equals(officer.getStaffId())) {
                throw new BankingException(FieldErrorCode.ALREADY_ASSIGNED);
            }
            current.end(now, actorId, "Reassigned");
            assignments.saveAndFlush(current);
        }
        CustomerAssignment assignment = assignments.saveAndFlush(new CustomerAssignment(UuidV7.next(), tenantId,
                customer.id(), officer.getStaffId(), now, actorId));
        FieldResponses.Assignment response = toResponse(assignment, customer);
        auditService.record(AuditEvent.builder("CUSTOMER_ASSIGNED", RESOURCE)
                .resourceId(assignment.getId())
                .resourceReference(customer.customerNumber())
                .branchId(officer.getBranchId())
                .before(current == null ? null : Map.of("officerId", current.getOfficerId()))
                .after(Map.of("officerId", officer.getStaffId(), "customerId", customer.id()))
                .build());
        return response;
    }

    @Transactional
    public FieldResponses.Assignment end(UUID assignmentId, OfficerRequests.EndAssignment request) {
        UUID tenantId = TenantContext.requireTenantId();
        CustomerAssignment assignment = assignments.findByTenantIdAndId(tenantId, assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment"));
        FieldOfficer officer = officerService.loadInScope(assignment.getOfficerId());
        assignment = assignments.lockActiveByCustomer(tenantId, assignment.getCustomerId())
                .filter(active -> active.getId().equals(assignmentId))
                .orElseThrow(() -> new BankingException(FieldErrorCode.ASSIGNMENT_NOT_ACTIVE));
        assignment.end(clock.instant(), CurrentActor.currentActorId().orElse(null), request.reason().trim());
        assignments.saveAndFlush(assignment);
        auditService.record(AuditEvent.builder("CUSTOMER_ASSIGNMENT_ENDED", RESOURCE)
                .resourceId(assignmentId)
                .branchId(officer.getBranchId())
                .metadata("reason", request.reason().trim())
                .build());
        return toResponse(assignment, customerService.summaries(List.of(assignment.getCustomerId()))
                .get(assignment.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<FieldResponses.Assignment> search(UUID officerId, UUID customerId, boolean activeOnly,
                                                         PageRequest page) {
        if (officerId != null) {
            officerService.loadInScope(officerId);
        }
        if (customerId != null) {
            customerService.getSummary(customerId);
        }
        Page<CustomerAssignment> found = assignments.search(TenantContext.requireTenantId(), officerId, customerId,
                activeOnly, page);
        Map<UUID, CustomerSummary> customers = customerService.summaries(found.getContent().stream()
                .map(CustomerAssignment::getCustomerId).toList());
        return PageResponse.from(found, assignment -> toResponse(assignment,
                customers.get(assignment.getCustomerId())));
    }

    /**
     * The signed-in officer's customers and the accounts a collection can be credited to (the field app keeps
     * these to work offline).
     */
    @Transactional(readOnly = true)
    public List<FieldResponses.MyCustomer> myCustomers() {
        FieldOfficer officer = officerService.requireCurrentOfficer();
        List<CustomerAssignment> mine = assignments.findActiveByOfficer(officer.getTenantId(), officer.getStaffId());
        List<UUID> customerIds = mine.stream().map(CustomerAssignment::getCustomerId).toList();
        Map<UUID, CustomerSummary> customers = customerService.summaries(customerIds);
        Map<UUID, List<SusuDtos.CollectablePlan>> plans = susuPlans.collectablePlans(customerIds);
        return mine.stream()
                .map(assignment -> customers.get(assignment.getCustomerId()))
                .filter(customer -> customer != null)
                .map(customer -> new FieldResponses.MyCustomer(customer.id(), customer.customerNumber(),
                        customer.displayName(), customer.primaryPhone(), collectableAccounts(customer.id(), officer),
                        plans.getOrDefault(customer.id(), List.of())))
                .toList();
    }

    // ----------------------------------------------------------------------------------- for the module

    /**
     * Whether the customer is assigned to the officer now; the assignment stays locked until the caller's
     * transaction ends, so a reassignment cannot slip in while a collection posts.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    boolean lockIsAssigned(UUID customerId, UUID officerId) {
        return assignments.lockActiveByCustomer(TenantContext.requireTenantId(), customerId)
                .map(assignment -> assignment.getOfficerId().equals(officerId))
                .orElse(false);
    }

    private List<FieldResponses.CollectableAccount> collectableAccounts(UUID customerId, FieldOfficer officer) {
        try {
            return accountService.heldBy(customerId).stream()
                    .filter(account -> COLLECTABLE.contains(account.status())
                            && account.currency().equals(officer.getCurrency()))
                    .map(CustomerAssignmentService::toCollectable)
                    .toList();
        } catch (ResourceNotFoundException outsideScope) {
            return List.of(); // the customer moved out of the officer's branches since being assigned
        }
    }

    private static FieldResponses.CollectableAccount toCollectable(AccountSummary account) {
        return new FieldResponses.CollectableAccount(account.id(), account.accountNumber(), account.title(),
                account.productCode(), account.productType(), account.currency(), account.status());
    }

    private static FieldResponses.Assignment toResponse(CustomerAssignment assignment, CustomerSummary customer) {
        return new FieldResponses.Assignment(assignment.getId(), assignment.getCustomerId(),
                customer == null ? null : customer.customerNumber(), customer == null ? null : customer.displayName(),
                assignment.getOfficerId(), assignment.getAssignedAt(), assignment.getAssignedBy(),
                assignment.getEndedAt(), assignment.getEndReason());
    }
}
