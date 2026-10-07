package com.company.banking.staff.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.ActorType;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.dto.IssuedCredential;
import com.company.banking.iam.dto.RoleSummary;
import com.company.banking.iam.service.RoleService;
import com.company.banking.iam.service.SessionService;
import com.company.banking.iam.service.StaffCredentialService;
import com.company.banking.staff.dto.ChangeStaffStatusRequest;
import com.company.banking.staff.dto.CreateStaffRequest;
import com.company.banking.staff.dto.MeResponse;
import com.company.banking.staff.dto.NewAdministrator;
import com.company.banking.staff.dto.StaffCreatedResponse;
import com.company.banking.staff.dto.StaffResponse;
import com.company.banking.staff.dto.StaffSummary;
import com.company.banking.staff.dto.UpdateStaffRequest;
import com.company.banking.staff.entity.Staff;
import com.company.banking.staff.entity.StaffStatus;
import com.company.banking.staff.mapper.StaffMapper;
import com.company.banking.staff.repository.StaffRepository;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.service.TenantService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Staff administration. Staff outside the caller's branch scope are invisible (reported as not found), and nobody
 * can change their own access through these operations.
 */
@Service
@RequiredArgsConstructor
public class StaffService {

    private static final String RESOURCE = "STAFF";
    private static final String ADMIN_EMPLOYEE_NUMBER = "ADMIN-0001";

    private final StaffRepository staffRepository;
    private final StaffMapper staffMapper;
    private final BranchService branchService;
    private final RoleService roleService;
    private final StaffCredentialService credentialService;
    private final TenantService tenantService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public PageResponse<StaffSummary> list(String query, UUID branchId, String status, PageRequest page) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        Specification<Staff> specification = (root, cq, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (!scope.allBranches()) {
                predicates.add(scope.branchIds().isEmpty()
                        ? cb.disjunction()
                        : root.get("homeBranchId").in(scope.branchIds()));
            }
            if (branchId != null) {
                predicates.add(cb.equal(root.get("homeBranchId"), branchId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), StaffStatus.valueOf(status)));
            }
            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("firstName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("lastName")), pattern, '\\'),
                        cb.like(cb.lower(root.get("email")), pattern, '\\'),
                        cb.like(cb.lower(root.get("employeeNumber")), pattern, '\\')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        PageRequest sorted = PageRequest.of(page.getPageNumber(), page.getPageSize(),
                Sort.by("lastName", "firstName"));
        return PageResponse.from(staffRepository.findAll(specification, sorted), staffMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public StaffResponse get(UUID staffId) {
        return toResponse(loadInScope(staffId));
    }

    /**
     * For other modules (e.g. relationship-officer assignment): an active staff member of this institution.
     */
    @Transactional(readOnly = true)
    public boolean isActiveStaff(UUID staffId) {
        return staffRepository.findByTenantIdAndId(TenantContext.requireTenantId(), staffId)
                .map(Staff::isActive)
                .orElse(false);
    }

    @Transactional
    public StaffCreatedResponse create(CreateStaffRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        if (request.allBranchesAccess() && !actor.branchScope().allBranches()) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                    "Only staff with access to all branches can grant it.");
        }
        if (!request.roleIdsOrEmpty().isEmpty() && actor.type() != ActorType.SYSTEM
                && !actor.hasPermission(Permissions.ROLE_ASSIGN)) {
            throw new BankingException(CommonErrorCode.ACCESS_DENIED, "Assigning roles requires role.assign.");
        }
        requireAssignableBranch(request.homeBranchId(), actor.branchScope());
        Staff staff = newStaff(request.employeeNumber(), request.firstName(), request.lastName(), request.email(),
                request.phone(), request.jobTitle(), request.homeBranchId(), request.allBranchesAccess());
        IssuedCredential credential = credentialService.createWithTemporaryPassword(staff.getId(),
                request.username());
        roleService.assignInitialRoles(staff.getId(), request.roleIdsOrEmpty());
        StaffResponse created = toResponse(staff);
        auditService.record(AuditEvent.builder("STAFF_CREATED", RESOURCE)
                .resourceId(staff.getId())
                .resourceReference(staff.getEmployeeNumber())
                .branchId(staff.getHomeBranchId())
                .after(Map.of("staff", snapshot(staff), "username", credential.username(),
                        "roles", created.roles().stream().map(RoleSummary::code).toList()))
                .build());
        return new StaffCreatedResponse(created, credential);
    }

    /**
     * First administrator of a newly onboarded institution: all-branch scope, administrator role.
     */
    @Transactional
    public StaffCreatedResponse provisionAdministrator(NewAdministrator administrator, UUID headOfficeId,
                                                      UUID administratorRoleId) {
        Staff staff = newStaff(ADMIN_EMPLOYEE_NUMBER, administrator.firstName(), administrator.lastName(),
                administrator.email(), administrator.phone(), "Institution Administrator", headOfficeId, true);
        IssuedCredential credential = credentialService.createWithTemporaryPassword(staff.getId(),
                administrator.username());
        roleService.assignInitialRoles(staff.getId(), Set.of(administratorRoleId));
        StaffResponse created = toResponse(staff);
        auditService.record(AuditEvent.builder("STAFF_CREATED", RESOURCE)
                .resourceId(staff.getId())
                .resourceReference(staff.getEmployeeNumber())
                .branchId(headOfficeId)
                .after(Map.of("staff", snapshot(staff), "username", credential.username(),
                        "roles", created.roles().stream().map(RoleSummary::code).toList()))
                .build());
        return new StaffCreatedResponse(created, credential);
    }

    @Transactional
    public StaffResponse update(UUID staffId, UpdateStaffRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        assertNotSelf(actor, staffId);
        Staff staff = loadInScope(staffId);
        ConcurrentModificationException.assertVersion(request.version(), staff.getVersion());
        boolean scopeChanged = !staff.getHomeBranchId().equals(request.homeBranchId())
                || staff.isAllBranchesAccess() != request.allBranchesAccess();
        if (scopeChanged) {
            if (request.allBranchesAccess() && !actor.branchScope().allBranches()) {
                throw new BankingException(CommonErrorCode.ACCESS_DENIED,
                        "Only staff with access to all branches can grant it.");
            }
            requireAssignableBranch(request.homeBranchId(), actor.branchScope());
        }
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (staffRepository.emailTakenByOther(staff.getTenantId(), email, staffId)) {
            throw new DuplicateResourceException("Another staff member already uses this email address.");
        }
        StaffSnapshot before = snapshot(staff);
        staff.updateProfile(request.firstName().trim(), request.lastName().trim(), email, request.phone(),
                request.jobTitle());
        staff.changeAccessScope(request.homeBranchId(), request.allBranchesAccess());
        staffRepository.saveAndFlush(staff);
        if (scopeChanged) {
            credentialService.revokeSessions(staffId, SessionService.REASON_ACCESS_CHANGED);
        }
        auditService.record(AuditEvent.builder("STAFF_UPDATED", RESOURCE)
                .resourceId(staffId)
                .resourceReference(staff.getEmployeeNumber())
                .branchId(staff.getHomeBranchId())
                .before(before)
                .after(snapshot(staff))
                .build());
        return toResponse(staff);
    }

    @Transactional
    public StaffResponse changeStatus(UUID staffId, ChangeStaffStatusRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        assertNotSelf(actor, staffId);
        Staff staff = loadInScope(staffId);
        ConcurrentModificationException.assertVersion(request.version(), staff.getVersion());
        StaffStatus previous = staff.getStatus();
        StaffStatus target = StaffStatus.valueOf(request.status());
        staff.changeStatus(target, request.reason().trim());
        staffRepository.saveAndFlush(staff);
        credentialService.setLoginEnabled(staffId, target == StaffStatus.ACTIVE,
                SessionService.REASON_ACCOUNT_DISABLED);
        auditService.record(AuditEvent.builder(statusAction(target), RESOURCE)
                .resourceId(staffId)
                .resourceReference(staff.getEmployeeNumber())
                .branchId(staff.getHomeBranchId())
                .before(Map.of("status", previous))
                .after(Map.of("status", target))
                .metadata("reason", request.reason().trim())
                .build());
        return toResponse(staff);
    }

    @Transactional
    public IssuedCredential resetCredential(UUID staffId) {
        AuthenticatedActor actor = CurrentActor.require();
        assertNotSelf(actor, staffId);
        Staff staff = loadInScope(staffId);
        if (staff.getStatus() == StaffStatus.TERMINATED) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Credentials of terminated staff cannot be reset.");
        }
        IssuedCredential credential = credentialService.resetWithTemporaryPassword(staffId);
        auditService.record(AuditEvent.builder("STAFF_CREDENTIAL_RESET", RESOURCE)
                .resourceId(staffId)
                .resourceReference(staff.getEmployeeNumber())
                .branchId(staff.getHomeBranchId())
                .build());
        return credential;
    }

    @Transactional(readOnly = true)
    public MeResponse me() {
        AuthenticatedActor actor = CurrentActor.require();
        Staff staff = staffRepository.findByTenantIdAndId(TenantContext.requireTenantId(), actor.id())
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
        TenantSummary tenant = tenantService.getCurrent();
        return new MeResponse(staff.getId(), tenant.id(), tenant.code(), tenant.displayName(), actor.username(),
                staff.getFirstName(), staff.getLastName(), staff.getEmail(), staff.getHomeBranchId(),
                staff.isAllBranchesAccess(), roleService.rolesOf(staff.getId()),
                actor.permissions().stream().sorted().toList(), actor.passwordChangeRequired());
    }

    private Staff newStaff(String employeeNumber, String firstName, String lastName, String email, String phone,
                           String jobTitle, UUID homeBranchId, boolean allBranchesAccess) {
        UUID tenantId = TenantContext.requireTenantId();
        String normalizedEmployeeNumber = employeeNumber.trim().toUpperCase(Locale.ROOT);
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (staffRepository.existsByTenantIdAndEmployeeNumber(tenantId, normalizedEmployeeNumber)) {
            throw new DuplicateResourceException("Employee number " + normalizedEmployeeNumber + " is already in use.");
        }
        if (staffRepository.emailTaken(tenantId, normalizedEmail)) {
            throw new DuplicateResourceException("Another staff member already uses this email address.");
        }
        return staffRepository.saveAndFlush(new Staff(UuidV7.next(), tenantId, normalizedEmployeeNumber,
                firstName.trim(), lastName.trim(), normalizedEmail, phone, jobTitle, homeBranchId,
                allBranchesAccess));
    }

    private void requireAssignableBranch(UUID branchId, BranchScope actorScope) {
        if (!actorScope.permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        BranchResponse branch = branchService.getForInternalUse(branchId);
        if (!branch.isActive()) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION,
                    "Staff can only be assigned to an active branch.");
        }
    }

    private Staff loadInScope(UUID staffId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return staffRepository.findByTenantIdAndId(TenantContext.requireTenantId(), staffId)
                .filter(staff -> scope.permits(staff.getHomeBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
    }

    private StaffResponse toResponse(Staff staff) {
        return staffMapper.toResponse(staff, credentialService.credentialInfo(staff.getId()).orElse(null),
                roleService.rolesOf(staff.getId()));
    }

    private static void assertNotSelf(AuthenticatedActor actor, UUID staffId) {
        if (actor.isSameUserAs(staffId)) {
            throw new BankingException(CommonErrorCode.SELF_MODIFICATION_NOT_ALLOWED);
        }
    }

    private static String statusAction(StaffStatus target) {
        return switch (target) {
            case ACTIVE -> "STAFF_REACTIVATED";
            case SUSPENDED -> "STAFF_SUSPENDED";
            case TERMINATED -> "STAFF_TERMINATED";
        };
    }

    private static StaffSnapshot snapshot(Staff staff) {
        return new StaffSnapshot(staff.getEmployeeNumber(), staff.getFirstName(), staff.getLastName(),
                staff.getEmail(), staff.getPhone(), staff.getJobTitle(), staff.getHomeBranchId(),
                staff.isAllBranchesAccess(), staff.getStatus().name());
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private record StaffSnapshot(String employeeNumber, String firstName, String lastName, String email,
                                 String phone, String jobTitle, UUID homeBranchId, boolean allBranchesAccess,
                                 String status) {
    }
}
