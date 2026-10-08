package com.company.banking.approval.service;

import com.company.banking.approval.dto.ApprovalPolicyRequest;
import com.company.banking.approval.dto.ApprovalPolicyResponse;
import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.approval.dto.ApprovalSubmission;
import com.company.banking.approval.dto.DecisionRequest;
import com.company.banking.approval.entity.ApprovalPolicy;
import com.company.banking.approval.entity.ApprovalRequest;
import com.company.banking.approval.exception.ApprovalErrorCode;
import com.company.banking.approval.model.ApprovalStatus;
import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.repository.ApprovalPolicyRepository;
import com.company.banking.approval.repository.ApprovalRequestRepository;
import com.company.banking.approval.service.ApprovalHandler.ApprovedAction;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.outbox.OutboxService;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.service.CurrencyService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Maker-checker. A maker submits an action; a different person with the right permissions and branch scope
 * approves it, which runs the action in the same transaction, or rejects it. Every step is audited.
 */
@Service
public class ApprovalService {

    private static final String RESOURCE = "APPROVAL_REQUEST";

    private final ApprovalRequestRepository requests;
    private final ApprovalPolicyRepository policies;
    // Looked up when first needed: the handlers' modules depend on this service to submit requests.
    private final ObjectProvider<ApprovalHandler> handlerProvider;
    private final AuditService auditService;
    private final OutboxService outbox;
    private final CurrencyService currencies;
    private final JsonMapper jsonMapper;
    private final Clock clock;
    private volatile Map<ApprovalType, ApprovalHandler> handlers;

    public ApprovalService(ApprovalRequestRepository requests, ApprovalPolicyRepository policies,
                           ObjectProvider<ApprovalHandler> handlerProvider, AuditService auditService,
                           OutboxService outbox, CurrencyService currencies, JsonMapper jsonMapper, Clock clock) {
        this.requests = requests;
        this.policies = policies;
        this.handlerProvider = handlerProvider;
        this.auditService = auditService;
        this.outbox = outbox;
        this.currencies = currencies;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    private ApprovalHandler handlerFor(ApprovalType type) {
        Map<ApprovalType, ApprovalHandler> known = handlers;
        if (known == null) {
            known = new EnumMap<>(ApprovalType.class);
            for (ApprovalHandler handler : handlerProvider.orderedStream().toList()) {
                if (known.put(handler.type(), handler) != null) {
                    throw new IllegalStateException("Two approval handlers for " + handler.type());
                }
            }
            handlers = known;
        }
        ApprovalHandler handler = known.get(type);
        if (handler == null) {
            throw new IllegalStateException("No approval handler for " + type);
        }
        return handler;
    }

    /**
     * Whether a movement needs a checker under the institution's thresholds.
     */
    @Transactional(readOnly = true)
    public boolean requiresApproval(ApprovalType type, String currency, BigDecimal amount) {
        return type.thresholdBased() && policies.findById(new ApprovalPolicy.Key(TenantContext.requireTenantId(),
                        type, currency))
                .filter(ApprovalPolicy::isActive)
                .filter(policy -> amount.compareTo(policy.getThresholdAmount()) >= 0)
                .isPresent();
    }

    /**
     * Records a request in the caller's transaction (it disappears if the caller rolls back).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ApprovalResponse submit(ApprovalSubmission submission) {
        UUID tenantId = TenantContext.requireTenantId();
        AuthenticatedActor maker = CurrentActor.require();
        if (maker.id() == null) {
            throw new IllegalStateException("Only a person can ask for an approval");
        }
        if (!maker.branchScope().permits(submission.branchId())) {
            throw new ResourceNotFoundException("Branch");
        }
        if (submission.resourceId() != null && requests.existsByTenantIdAndRequestTypeAndResourceIdAndStatus(
                tenantId, submission.type(), submission.resourceId(), ApprovalStatus.PENDING)) {
            throw new BankingException(ApprovalErrorCode.APPROVAL_ALREADY_PENDING);
        }
        ApprovalRequest request = requests.saveAndFlush(new ApprovalRequest(UuidV7.next(), tenantId,
                submission.type(), submission.branchId(), submission.amount(), submission.currency(),
                submission.resourceType(), submission.resourceId(), truncate(submission.summary()),
                jsonMapper.writeValueAsString(submission.payload()), maker.id(), clock.instant()));
        auditService.record(AuditEvent.builder("APPROVAL_REQUESTED", RESOURCE)
                .resourceId(request.getId())
                .branchId(request.getBranchId())
                .metadata("requestType", request.getRequestType())
                .metadata("summary", request.getSummary())
                .build());
        outbox.publish(RESOURCE, request.getId(), "APPROVAL_REQUESTED", Map.of(
                "requestType", request.getRequestType().name(), "branchId", request.getBranchId().toString()));
        return toResponse(request);
    }

    @Transactional(readOnly = true)
    public PageResponse<ApprovalResponse> search(String status, String type, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<ApprovalRequest> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("branchId").in(scope.branchIds()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), ApprovalStatus.valueOf(status)));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("requestType"), ApprovalType.valueOf(type)));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return PageResponse.from(requests.findAll(specification, PageRequest.of(page.getPageNumber(),
                page.getPageSize(), Sort.by("requestedAt").ascending().and(Sort.by("id")))), this::toResponse);
    }

    /**
     * Reviewers see every request in their branch scope; a maker without {@code approval.view} sees their own.
     */
    @Transactional(readOnly = true)
    public ApprovalResponse get(UUID requestId) {
        AuthenticatedActor actor = CurrentActor.require();
        ApprovalRequest request = requests.findByTenantIdAndId(TenantContext.requireTenantId(), requestId)
                .filter(found -> actor.hasPermission(Permissions.APPROVAL_VIEW)
                        ? actor.branchScope().permits(found.getBranchId())
                        : actor.isSameUserAs(found.getRequestedBy()))
                .orElseThrow(() -> new ResourceNotFoundException("Approval request"));
        return toResponse(request);
    }

    /**
     * Approves and runs the action. The checker must differ from the maker, hold the permission the action needs
     * and have the request's branch in scope.
     */
    @Transactional
    public ApprovalResponse approve(UUID requestId, DecisionRequest decision) {
        AuthenticatedActor checker = CurrentActor.require();
        ApprovalRequest request = lockForDecision(requestId, decision, checker);
        UUID result = handlerFor(request.getRequestType()).execute(new ApprovedAction(request.getId(), request.getRequestType(),
                request.getRequestedBy(), checker.id(), request.getResourceId(), request.getPayload()));
        request.decide(ApprovalStatus.APPROVED, checker.id(), clock.instant(), blankToNull(decision.note()), result);
        requests.saveAndFlush(request);
        audit("APPROVAL_APPROVED", request);
        return toResponse(request);
    }

    @Transactional
    public ApprovalResponse reject(UUID requestId, DecisionRequest decision) {
        AuthenticatedActor checker = CurrentActor.require();
        if (decision.note() == null || decision.note().isBlank()) {
            throw new BankingException(ApprovalErrorCode.DECISION_NOTE_REQUIRED);
        }
        ApprovalRequest request = lockForDecision(requestId, decision, checker);
        request.decide(ApprovalStatus.REJECTED, checker.id(), clock.instant(), decision.note().trim(), null);
        requests.saveAndFlush(request);
        audit("APPROVAL_REJECTED", request);
        return toResponse(request);
    }

    /**
     * The maker withdraws their own request.
     */
    @Transactional
    public ApprovalResponse cancel(UUID requestId, DecisionRequest decision) {
        AuthenticatedActor maker = CurrentActor.require();
        ApprovalRequest request = requests.lockByTenantIdAndId(TenantContext.requireTenantId(), requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Approval request"));
        if (!maker.isSameUserAs(request.getRequestedBy())) {
            throw new BankingException(ApprovalErrorCode.NOT_THE_REQUESTER);
        }
        ConcurrentModificationException.assertVersion(decision.version(), request.getVersion());
        if (!request.isPending()) {
            throw new BankingException(ApprovalErrorCode.APPROVAL_NOT_PENDING);
        }
        request.decide(ApprovalStatus.CANCELLED, maker.id(), clock.instant(), blankToNull(decision.note()), null);
        requests.saveAndFlush(request);
        audit("APPROVAL_CANCELLED", request);
        return toResponse(request);
    }

    // ------------------------------------------------------------------------------------------------- policies

    @Transactional(readOnly = true)
    public List<ApprovalPolicyResponse> policies() {
        return policies.findAllOfTenant(TenantContext.requireTenantId()).stream().map(ApprovalService::toResponse)
                .toList();
    }

    @Transactional
    public ApprovalPolicyResponse configure(ApprovalType type, String currency, ApprovalPolicyRequest request) {
        if (!type.thresholdBased()) {
            throw new BankingException(ApprovalErrorCode.POLICY_NOT_CONFIGURABLE);
        }
        currencies.require(currency);
        ApprovalPolicy.Key key = new ApprovalPolicy.Key(TenantContext.requireTenantId(), type, currency);
        ApprovalPolicy policy = policies.findById(key).orElseGet(() -> new ApprovalPolicy(key));
        ApprovalPolicyResponse before = policy.getThresholdAmount() == null ? null : toResponse(policy);
        policy.configure(request.thresholdAmount(), request.active(), clock.instant(),
                CurrentActor.currentActorId().orElse(null));
        ApprovalPolicyResponse after = toResponse(policies.saveAndFlush(policy));
        auditService.record(AuditEvent.builder("APPROVAL_POLICY_CONFIGURED", "APPROVAL_POLICY")
                .resourceId(type + ":" + currency)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    // ---------------------------------------------------------------------------------------------------------

    private ApprovalRequest lockForDecision(UUID requestId, DecisionRequest decision, AuthenticatedActor checker) {
        ApprovalRequest request = requests.lockByTenantIdAndId(TenantContext.requireTenantId(), requestId)
                .filter(found -> checker.branchScope().permits(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Approval request"));
        ConcurrentModificationException.assertVersion(decision.version(), request.getVersion());
        if (!request.isPending()) {
            throw new BankingException(ApprovalErrorCode.APPROVAL_NOT_PENDING);
        }
        if (checker.id() == null || checker.isSameUserAs(request.getRequestedBy())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
        if (!checker.hasPermission(request.getRequestType().checkerPermission())) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                    "Approving this needs the " + request.getRequestType().checkerPermission() + " permission.");
        }
        return request;
    }

    private void audit(String action, ApprovalRequest request) {
        auditService.record(AuditEvent.builder(action, RESOURCE)
                .resourceId(request.getId())
                .branchId(request.getBranchId())
                .metadata("requestType", request.getRequestType())
                .metadata("requestedBy", request.getRequestedBy())
                .metadata("note", request.getDecisionNote())
                .metadata("result", request.getResultResourceId())
                .build());
    }

    private ApprovalResponse toResponse(ApprovalRequest request) {
        return new ApprovalResponse(request.getId(), request.getRequestType().name(), request.getStatus().name(),
                request.getBranchId(), request.getAmount() == null ? null
                        : currencies.present(request.getAmount(), request.getCurrency()), request.getCurrency(),
                request.getResourceType(),
                request.getResourceId(), request.getSummary(), jsonMapper.readTree(request.getPayload()),
                request.getRequestedBy(), request.getRequestedAt(), request.getDecidedBy(), request.getDecidedAt(),
                request.getDecisionNote(), request.getResultResourceId(), request.getVersion());
    }

    private static ApprovalPolicyResponse toResponse(ApprovalPolicy policy) {
        return new ApprovalPolicyResponse(policy.getId().requestType().name(), policy.getId().currency(),
                policy.getThresholdAmount(), policy.isActive(), policy.getUpdatedAt(), policy.getUpdatedBy());
    }

    private static String truncate(String summary) {
        return summary.length() <= 300 ? summary : summary.substring(0, 300);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
