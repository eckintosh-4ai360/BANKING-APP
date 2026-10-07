package com.company.banking.kyc.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.dto.CustomerKycSnapshot;
import com.company.banking.customer.dto.CustomerSummary;
import com.company.banking.customer.dto.IdentityVerificationSubject;
import com.company.banking.customer.service.CustomerKycService;
import com.company.banking.kyc.dto.KycCaseResponse;
import com.company.banking.kyc.dto.KycCaseSummary;
import com.company.banking.kyc.dto.KycDecisionRequest;
import com.company.banking.kyc.dto.KycNoteRequest;
import com.company.banking.kyc.dto.ManualCheckRequest;
import com.company.banking.kyc.dto.OpenKycCaseRequest;
import com.company.banking.kyc.dto.RequirementStatus;
import com.company.banking.kyc.entity.KycCase;
import com.company.banking.kyc.entity.KycCaseStatus;
import com.company.banking.kyc.entity.KycCaseType;
import com.company.banking.kyc.entity.KycCheck;
import com.company.banking.kyc.entity.KycTier;
import com.company.banking.kyc.exception.KycErrorCode;
import com.company.banking.kyc.mapper.KycMapper;
import com.company.banking.kyc.repository.KycCaseRepository;
import com.company.banking.kyc.repository.KycCheckRepository;
import com.company.banking.kyc.spi.IdentityVerificationProvider;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * KYC case workflow. Officers capture evidence and submit; reviewers return, approve or reject. Whoever submitted a
 * case can never decide it (checked here and by a database constraint).
 */
@Service
@RequiredArgsConstructor
public class KycCaseService {

    private static final String RESOURCE = "KYC_CASE";
    private static final Set<String> SCREENING_CHECKS = Set.of("WATCHLIST", "PEP");

    private final KycCaseRepository caseRepository;
    private final KycCheckRepository checkRepository;
    private final KycTierService tierService;
    private final KycRequirementEvaluator evaluator;
    private final IdentityVerificationGateway verificationGateway;
    private final CustomerKycService customerKycService;
    private final KycMapper mapper;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional
    public KycCaseResponse open(UUID customerId, OpenKycCaseRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        UUID tenantId = TenantContext.requireTenantId();
        KycTier tier = tierService.requireActive(request.targetTierCode());
        KycCaseType type = KycCaseType.valueOf(request.caseType());
        CustomerKycSnapshot before = customerKycService.snapshot(customerId);
        if (caseRepository.existsByTenantIdAndCustomerIdAndStatusIn(tenantId, customerId, KycCaseStatus.UNDECIDED)) {
            throw new BankingException(KycErrorCode.KYC_CASE_ALREADY_OPEN);
        }
        assertCaseTypeFits(type, tier, before);

        CustomerKycSnapshot customer = customerKycService.kycStarted(customerId);
        KycCase kycCase = caseRepository.saveAndFlush(new KycCase(UuidV7.next(), tenantId, customerId,
                customer.homeBranchId(), type, tier.getCode(), actor.id()));
        audit("KYC_CASE_OPENED", kycCase, customer, Map.of("caseType", type, "targetTier", tier.getCode()));
        return toResponse(kycCase, customer);
    }

    @Transactional(readOnly = true)
    public KycCaseResponse get(UUID caseId) {
        KycCase kycCase = loadInScope(caseId, false);
        return toResponse(kycCase, customerKycService.snapshot(kycCase.getCustomerId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<KycCaseSummary> search(String status, UUID branchId, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<KycCase> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("branchId").in(scope.branchIds()));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), KycCaseStatus.valueOf(status)));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("branchId"), branchId));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        Page<KycCase> cases = caseRepository.findAll(specification, PageRequest.of(page.getPageNumber(),
                page.getPageSize(), Sort.by("createdAt").ascending().and(Sort.by("id"))));
        Map<UUID, CustomerSummary> customers = customerKycService.summaries(
                cases.getContent().stream().map(KycCase::getCustomerId).toList());
        return PageResponse.from(cases, kycCase -> toSummary(kycCase, customers.get(kycCase.getCustomerId())));
    }

    @Transactional(readOnly = true)
    public List<KycCaseSummary> casesOfCustomer(UUID customerId) {
        CustomerKycSnapshot customer = customerKycService.snapshot(customerId);
        CustomerSummary summary = customerKycService.summaries(List.of(customerId)).get(customerId);
        return caseRepository.findByTenantIdAndCustomerIdOrderByCreatedAtDesc(TenantContext.requireTenantId(),
                        customer.customerId()).stream()
                .map(kycCase -> toSummary(kycCase, summary))
                .toList();
    }

    /**
     * Electronic identity verification. The provider is called outside any database transaction, so a slow
     * provider never holds locks; the result is then recorded in a short transaction.
     */
    public KycCaseResponse runIdentityCheck(UUID caseId) {
        KycCase kycCase = transactionTemplate.execute(status -> {
            KycCase found = loadInScope(caseId, false);
            requireUndecided(found);
            return found;
        });
        IdentityVerificationSubject subject = customerKycService.identityVerificationSubject(kycCase.getCustomerId());
        IdentityVerificationProvider provider = verificationGateway
                .providerFor(subject.idTypeCode(), subject.issuingCountry())
                .orElseThrow(() -> new BankingException(KycErrorCode.IDENTITY_VERIFICATION_UNAVAILABLE));
        IdentityVerificationProvider.Result result = verificationGateway.verify(provider, subject);

        return transactionTemplate.execute(status -> {
            KycCase locked = loadInScope(caseId, true);
            requireUndecided(locked);
            String outcome = switch (result.outcome()) {
                case MATCH -> KycCheck.PASS;
                case NO_MATCH, NOT_FOUND -> KycCheck.FAIL;
                case ERROR -> "ERROR";
            };
            KycCheck check = checkRepository.saveAndFlush(new KycCheck(UuidV7.next(), locked.getTenantId(),
                    locked.getId(), KycCheck.IDENTITY_VERIFICATION, "ELECTRONIC", provider.name(), outcome,
                    result.score(), result.reference(), result.message(),
                    CurrentActor.currentActorId().orElse(null), clock.instant()));
            if (!"ERROR".equals(outcome)) {
                customerKycService.recordIdentificationVerification(locked.getCustomerId(),
                        subject.identificationId(), KycCheck.PASS.equals(outcome), result.reference());
            }
            CustomerKycSnapshot customer = customerKycService.snapshot(locked.getCustomerId());
            audit("KYC_CHECK_RECORDED", locked, customer, Map.of("checkType", check.getCheckType(),
                    "method", check.getMethod(), "result", check.getResult(), "provider", provider.name()));
            return toResponse(locked, customer);
        });
    }

    @Transactional
    public KycCaseResponse recordManualCheck(UUID caseId, ManualCheckRequest request) {
        KycCase kycCase = loadInScope(caseId, true);
        requireUndecided(kycCase);
        UUID actor = CurrentActor.require().id();
        KycCheck check = checkRepository.saveAndFlush(new KycCheck(UuidV7.next(), kycCase.getTenantId(),
                kycCase.getId(), request.checkType(), "MANUAL", null, request.result(), null, null,
                request.note().trim(), actor, clock.instant()));
        if (KycCheck.IDENTITY_VERIFICATION.equals(check.getCheckType()) && !"INCONCLUSIVE".equals(check.getResult())) {
            CustomerKycSnapshot snapshot = customerKycService.snapshot(kycCase.getCustomerId());
            if (snapshot.primaryIdentification() != null) {
                customerKycService.recordIdentificationVerification(kycCase.getCustomerId(),
                        snapshot.primaryIdentification().id(), check.passed(), "MANUAL-" + check.getId());
            }
        }
        CustomerKycSnapshot customer = customerKycService.snapshot(kycCase.getCustomerId());
        audit("KYC_CHECK_RECORDED", kycCase, customer, Map.of("checkType", check.getCheckType(),
                "method", check.getMethod(), "result", check.getResult()));
        return toResponse(kycCase, customer);
    }

    @Transactional
    public KycCaseResponse submit(UUID caseId) {
        KycCase kycCase = loadInScope(caseId, true);
        KycTier tier = tierService.requireActive(kycCase.getTargetTierCode());
        List<RequirementStatus> requirements = evaluator.evaluate(tier,
                customerKycService.snapshot(kycCase.getCustomerId()), checks(kycCase));
        List<String> unmet = KycRequirementEvaluator.unmetForSubmission(requirements);
        if (!unmet.isEmpty()) {
            throw new BankingException(KycErrorCode.KYC_REQUIREMENTS_NOT_MET,
                    "Missing before submission: " + String.join(", ", unmet) + ".");
        }
        CustomerKycSnapshot customer = customerKycService.kycSubmitted(kycCase.getCustomerId());
        kycCase.submit(CurrentActor.require().id(), clock.instant());
        caseRepository.saveAndFlush(kycCase);
        audit("KYC_CASE_SUBMITTED", kycCase, customer, null);
        return toResponse(kycCase, customer);
    }

    @Transactional
    public KycCaseResponse returnForCorrection(UUID caseId, KycNoteRequest request) {
        KycCase kycCase = loadInScope(caseId, true);
        assertNotSubmitter(kycCase);
        ConcurrentModificationException.assertVersion(request.version(), kycCase.getVersion());
        kycCase.returnForCorrection(CurrentActor.require().id(), request.note().trim());
        customerKycService.kycReturned(kycCase.getCustomerId());
        caseRepository.saveAndFlush(kycCase);
        CustomerKycSnapshot customer = customerKycService.snapshot(kycCase.getCustomerId());
        audit("KYC_CASE_RETURNED", kycCase, customer, Map.of("note", request.note().trim()));
        return toResponse(kycCase, customer);
    }

    @Transactional
    public KycCaseResponse approve(UUID caseId, KycDecisionRequest request) {
        KycCase kycCase = loadInScope(caseId, true);
        assertNotSubmitter(kycCase);
        ConcurrentModificationException.assertVersion(request.version(), kycCase.getVersion());
        UUID checker = CurrentActor.require().id();
        KycTier tier = tierService.requireActive(kycCase.getTargetTierCode());
        List<KycCheck> checks = checks(kycCase);
        List<String> unmet = KycRequirementEvaluator.unmetForApproval(evaluator.evaluate(tier,
                customerKycService.snapshot(kycCase.getCustomerId()), checks));
        if (!unmet.isEmpty()) {
            throw new BankingException(KycErrorCode.KYC_REQUIREMENTS_NOT_MET,
                    "Not yet satisfied: " + String.join(", ", unmet) + ".");
        }
        boolean screeningHit = checks.stream().anyMatch(check ->
                SCREENING_CHECKS.contains(check.getCheckType()) && KycCheck.FAIL.equals(check.getResult()));
        if (screeningHit && !"HIGH".equals(request.riskLevel())) {
            throw new BankingException(KycErrorCode.HIGH_RISK_REQUIRED);
        }
        kycCase.approve(checker, request.riskLevel(), request.note(), clock.instant());
        caseRepository.saveAndFlush(kycCase);
        customerKycService.kycApproved(kycCase.getCustomerId(), tier.getCode(), request.riskLevel());
        CustomerKycSnapshot customer = customerKycService.snapshot(kycCase.getCustomerId());
        audit("KYC_CASE_APPROVED", kycCase, customer, Map.of("tier", tier.getCode(),
                "riskLevel", request.riskLevel()));
        return toResponse(kycCase, customer);
    }

    @Transactional
    public KycCaseResponse reject(UUID caseId, KycNoteRequest request) {
        KycCase kycCase = loadInScope(caseId, true);
        assertNotSubmitter(kycCase);
        ConcurrentModificationException.assertVersion(request.version(), kycCase.getVersion());
        kycCase.reject(CurrentActor.require().id(), request.note().trim(), clock.instant());
        caseRepository.saveAndFlush(kycCase);
        customerKycService.kycRejected(kycCase.getCustomerId());
        CustomerKycSnapshot customer = customerKycService.snapshot(kycCase.getCustomerId());
        audit("KYC_CASE_REJECTED", kycCase, customer, Map.of("note", request.note().trim()));
        return toResponse(kycCase, customer);
    }

    @Transactional
    public KycCaseResponse cancel(UUID caseId, KycNoteRequest request) {
        KycCase kycCase = loadInScope(caseId, true);
        ConcurrentModificationException.assertVersion(request.version(), kycCase.getVersion());
        kycCase.cancel(request.note().trim());
        customerKycService.kycCancelled(kycCase.getCustomerId());
        caseRepository.saveAndFlush(kycCase);
        CustomerKycSnapshot customer = customerKycService.snapshot(kycCase.getCustomerId());
        audit("KYC_CASE_CANCELLED", kycCase, customer, Map.of("note", request.note().trim()));
        return toResponse(kycCase, customer);
    }

    // ---------------------------------------------------------------------------------------------------------

    private void assertCaseTypeFits(KycCaseType type, KycTier tier, CustomerKycSnapshot customer) {
        boolean onboarding = "PENDING".equals(customer.status());
        boolean fits = switch (type) {
            case ONBOARDING -> onboarding;
            case PERIODIC_REVIEW, UPDATE -> !onboarding && customer.kycTierCode() != null;
            case UPGRADE -> !onboarding && customer.kycTierCode() != null
                    && tier.getTierRank() > tierService.rankOf(customer.kycTierCode());
        };
        if (!fits) {
            throw new BankingException(KycErrorCode.KYC_CASE_TYPE_NOT_ALLOWED);
        }
    }

    private KycCase loadInScope(UUID caseId, boolean lock) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        return (lock ? caseRepository.lockByTenantIdAndId(tenantId, caseId)
                : caseRepository.findByTenantIdAndId(tenantId, caseId))
                .filter(kycCase -> scope.permits(kycCase.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("KYC case"));
    }

    /**
     * Checked before anything else so a maker trying to decide their own case gets the clearest answer.
     */
    private static void assertNotSubmitter(KycCase kycCase) {
        UUID actor = CurrentActor.require().id();
        if (actor == null || actor.equals(kycCase.getSubmittedBy())) {
            throw new BankingException(CommonErrorCode.FOUR_EYES_VIOLATION);
        }
    }

    private static void requireUndecided(KycCase kycCase) {
        if (!KycCaseStatus.UNDECIDED.contains(kycCase.getStatus())) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Checks can only be recorded on an undecided case.");
        }
    }

    private List<KycCheck> checks(KycCase kycCase) {
        return checkRepository.findByTenantIdAndKycCaseIdOrderByPerformedAtAsc(kycCase.getTenantId(),
                kycCase.getId());
    }

    private KycCaseResponse toResponse(KycCase kycCase, CustomerKycSnapshot customer) {
        List<KycCheck> checks = checks(kycCase);
        List<RequirementStatus> requirements = evaluator.evaluate(
                tierService.requireActive(kycCase.getTargetTierCode()), customer, checks);
        return new KycCaseResponse(kycCase.getId(), kycCase.getCustomerId(), customer.customerNumber(),
                customer.displayName(), customer.kycStatus(), kycCase.getBranchId(), kycCase.getCaseType().name(),
                kycCase.getTargetTierCode(), kycCase.getStatus().name(), kycCase.getOpenedBy(),
                kycCase.getSubmittedBy(), kycCase.getSubmittedAt(), kycCase.getDecidedBy(), kycCase.getDecidedAt(),
                kycCase.getDecisionNote(), kycCase.getAssignedRiskLevel(), requirements,
                checks.stream().map(mapper::toResponse).toList(), kycCase.getCreatedAt(), kycCase.getVersion());
    }

    private static KycCaseSummary toSummary(KycCase kycCase, CustomerSummary customer) {
        return new KycCaseSummary(kycCase.getId(), kycCase.getCustomerId(),
                customer == null ? null : customer.customerNumber(), customer == null ? null : customer.displayName(),
                kycCase.getBranchId(), kycCase.getCaseType().name(), kycCase.getTargetTierCode(),
                kycCase.getStatus().name(), kycCase.getSubmittedBy(), kycCase.getSubmittedAt(),
                kycCase.getCreatedAt());
    }

    private void audit(String action, KycCase kycCase, CustomerKycSnapshot customer, Map<String, Object> details) {
        AuditEvent.Builder event = AuditEvent.builder(action, RESOURCE)
                .resourceId(kycCase.getId())
                .resourceReference(customer.customerNumber())
                .branchId(kycCase.getBranchId())
                .after(Map.of("status", kycCase.getStatus(), "customerKycStatus", customer.kycStatus()))
                .metadata("customerId", kycCase.getCustomerId());
        if (details != null) {
            details.forEach(event::metadata);
        }
        auditService.record(event.build());
    }
}
