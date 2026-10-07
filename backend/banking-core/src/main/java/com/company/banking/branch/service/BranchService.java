package com.company.banking.branch.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.dto.ChangeBranchStatusRequest;
import com.company.banking.branch.dto.CreateBranchRequest;
import com.company.banking.branch.dto.NewHeadOffice;
import com.company.banking.branch.dto.UpdateBranchRequest;
import com.company.banking.branch.entity.Branch;
import com.company.banking.branch.entity.BranchStatus;
import com.company.banking.branch.entity.BranchType;
import com.company.banking.branch.mapper.BranchMapper;
import com.company.banking.branch.repository.BranchRepository;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Branch management. Reads are limited to the actor's branch scope; resources outside it are reported as not
 * found.
 */
@Service
@RequiredArgsConstructor
public class BranchService {

    private static final String RESOURCE = "BRANCH";

    private final BranchRepository branchRepository;
    private final BranchMapper branchMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<BranchResponse> list(String status, String query, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<Branch> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("id").in(scope.branchIds()));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), BranchStatus.valueOf(status)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("name")), pattern, '\\'),
                        cb.like(cb.lower(root.get("code")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest sorted = PageRequest.of(page.getPageNumber(), page.getPageSize(), Sort.by("code"));
        return PageResponse.from(branchRepository.findAll(specification, sorted), branchMapper::toResponse);
    }

    @Transactional(readOnly = true)
    public BranchResponse get(UUID branchId) {
        return branchMapper.toResponse(loadInScope(branchId));
    }

    /**
     * Branch lookup for other modules (no actor scope applied; tenant isolation still applies).
     */
    @Transactional(readOnly = true)
    public BranchResponse getForInternalUse(UUID branchId) {
        return branchRepository.findByTenantIdAndId(TenantContext.requireTenantId(), branchId)
                .map(branchMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Branch"));
    }

    @Transactional
    public BranchResponse create(CreateBranchRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        if (!actor.branchScope().allBranches()) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                    "Only staff with access to all branches can create branches.");
        }
        UUID tenantId = TenantContext.requireTenantId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (branchRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateResourceException("A branch with code " + code + " already exists.");
        }
        Branch branch = new Branch(UuidV7.next(), tenantId, code, request.name().trim(),
                BranchType.valueOf(request.branchType()), request.openedOn());
        branch.updateDetails(request.name().trim(), request.phone(), request.email(), request.addressLine1(),
                request.addressLine2(), request.city(), request.region(), request.digitalAddress(),
                request.openedOn());
        BranchResponse created = branchMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditService.record(AuditEvent.builder("BRANCH_CREATED", RESOURCE)
                .resourceId(created.id())
                .resourceReference(created.code())
                .branchId(created.id())
                .after(created)
                .build());
        return created;
    }

    /**
     * Creates the head office while onboarding a new institution (no staff exist yet to hold a scope).
     */
    @Transactional
    public BranchResponse provisionHeadOffice(NewHeadOffice headOffice) {
        UUID tenantId = TenantContext.requireTenantId();
        Branch branch = new Branch(UuidV7.next(), tenantId, headOffice.code().trim().toUpperCase(Locale.ROOT),
                headOffice.name().trim(), BranchType.HEAD_OFFICE, LocalDate.now(clock));
        branch.updateDetails(headOffice.name().trim(), null, null, null, null, headOffice.city(),
                headOffice.region(), headOffice.digitalAddress(), LocalDate.now(clock));
        BranchResponse created = branchMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditService.record(AuditEvent.builder("BRANCH_CREATED", RESOURCE)
                .resourceId(created.id())
                .resourceReference(created.code())
                .branchId(created.id())
                .after(created)
                .build());
        return created;
    }

    @Transactional
    public BranchResponse update(UUID branchId, UpdateBranchRequest request) {
        Branch branch = loadInScope(branchId);
        ConcurrentModificationException.assertVersion(request.version(), branch.getVersion());
        BranchResponse before = branchMapper.toResponse(branch);
        branch.updateDetails(request.name().trim(), request.phone(), request.email(), request.addressLine1(),
                request.addressLine2(), request.city(), request.region(), request.digitalAddress(),
                request.openedOn());
        BranchResponse after = branchMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditService.record(AuditEvent.builder("BRANCH_UPDATED", RESOURCE)
                .resourceId(branchId)
                .resourceReference(branch.getCode())
                .branchId(branchId)
                .before(before)
                .after(after)
                .build());
        return after;
    }

    @Transactional
    public BranchResponse changeStatus(UUID branchId, ChangeBranchStatusRequest request) {
        Branch branch = loadInScope(branchId);
        ConcurrentModificationException.assertVersion(request.version(), branch.getVersion());
        BranchStatus previous = branch.getStatus();
        branch.changeStatus(BranchStatus.valueOf(request.status()), LocalDate.now(clock));
        BranchResponse after = branchMapper.toResponse(branchRepository.saveAndFlush(branch));
        auditService.record(AuditEvent.builder("BRANCH_STATUS_CHANGED", RESOURCE)
                .resourceId(branchId)
                .resourceReference(branch.getCode())
                .branchId(branchId)
                .before(Map.of("status", previous))
                .after(Map.of("status", branch.getStatus()))
                .metadata("reason", request.reason())
                .build());
        return after;
    }

    private Branch loadInScope(UUID branchId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return branchRepository.findByTenantIdAndId(TenantContext.requireTenantId(), branchId)
                .filter(branch -> scope.permits(branch.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Branch"));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
