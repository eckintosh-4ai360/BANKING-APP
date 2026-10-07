package com.company.banking.iam.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.dto.CreateRoleRequest;
import com.company.banking.iam.dto.PermissionResponse;
import com.company.banking.iam.dto.RoleResponse;
import com.company.banking.iam.dto.RoleSummary;
import com.company.banking.iam.dto.UpdateRoleRequest;
import com.company.banking.iam.entity.Permission;
import com.company.banking.iam.entity.PermissionScope;
import com.company.banking.iam.entity.PrincipalType;
import com.company.banking.iam.entity.Role;
import com.company.banking.iam.entity.RoleStatus;
import com.company.banking.iam.entity.StaffRole;
import com.company.banking.iam.exception.IamErrorCode;
import com.company.banking.iam.mapper.IamMapper;
import com.company.banking.iam.repository.PermissionRepository;
import com.company.banking.iam.repository.RoleRepository;
import com.company.banking.iam.repository.StaffRoleRepository;
import com.company.banking.iam.spi.StaffDirectory;
import com.company.banking.iam.spi.StaffDirectory.StaffAuthProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Roles, the permission catalog and role assignment.
 *
 * <p>Anti-escalation rules: nobody can change their own role assignments or edit a role they hold, and
 * platform-scope permissions can never be granted to an institution role (also enforced by a database trigger).
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    private static final String RESOURCE = "ROLE";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final StaffRoleRepository staffRoleRepository;
    private final StaffDirectory staffDirectory;
    private final SessionService sessionService;
    private final IamMapper iamMapper;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<PermissionResponse> tenantPermissionCatalog() {
        return permissionRepository.findByScopeOrderByCode(PermissionScope.TENANT).stream()
                .map(iamMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RoleResponse> list() {
        return roleRepository.findByTenantIdOrderByCode(TenantContext.requireTenantId()).stream()
                .map(iamMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RoleResponse get(UUID roleId) {
        return iamMapper.toResponse(load(roleId));
    }

    @Transactional
    public RoleResponse create(CreateRoleRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (roleRepository.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateResourceException("A role with code " + code + " already exists.");
        }
        Set<String> permissions = validatedTenantPermissions(request.permissions());
        Role role = roleRepository.saveAndFlush(new Role(UuidV7.next(), tenantId, code, request.name().trim(),
                request.description(), false, permissions));
        RoleResponse created = iamMapper.toResponse(role);
        auditService.record(AuditEvent.builder("ROLE_CREATED", RESOURCE)
                .resourceId(role.getId())
                .resourceReference(code)
                .after(created)
                .build());
        return created;
    }

    @Transactional
    public RoleResponse update(UUID roleId, UpdateRoleRequest request) {
        AuthenticatedActor actor = CurrentActor.require();
        Role role = load(roleId);
        ConcurrentModificationException.assertVersion(request.version(), role.getVersion());
        if (actor.id() != null && staffRoleRepository.existsByIdStaffIdAndIdRoleId(actor.id(), roleId)) {
            throw new BankingException(CommonErrorCode.SELF_MODIFICATION_NOT_ALLOWED,
                    "You cannot change a role that you hold.");
        }
        Set<String> permissions = validatedTenantPermissions(request.permissions());
        RoleResponse before = iamMapper.toResponse(role);
        role.update(request.name().trim(), request.description(), RoleStatus.valueOf(request.status()), permissions);
        RoleResponse after = iamMapper.toResponse(roleRepository.saveAndFlush(role));
        auditService.record(AuditEvent.builder("ROLE_UPDATED", RESOURCE)
                .resourceId(roleId)
                .resourceReference(role.getCode())
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * Replaces the staff member's roles. Their sessions are revoked so the new permissions apply immediately.
     */
    @Transactional
    public List<RoleSummary> assignRoles(UUID staffId, Set<UUID> roleIds) {
        AuthenticatedActor actor = CurrentActor.require();
        if (actor.isSameUserAs(staffId)) {
            throw new BankingException(CommonErrorCode.SELF_MODIFICATION_NOT_ALLOWED,
                    "You cannot change your own roles.");
        }
        StaffAuthProfile target = staffDirectory.findAuthProfile(staffId)
                .filter(profile -> actor.branchScope().permits(profile.homeBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Staff"));
        if (target.isTerminated()) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Roles cannot be assigned to terminated staff.");
        }
        List<RoleSummary> before = rolesOf(staffId);
        List<RoleSummary> after = replaceAssignments(staffId, roleIds, actor.id());
        sessionService.revokeAllOfPrincipal(PrincipalType.STAFF, staffId, SessionService.REASON_ROLES_CHANGED);
        auditService.record(AuditEvent.builder("STAFF_ROLES_CHANGED", "STAFF")
                .resourceId(staffId)
                .branchId(target.homeBranchId())
                .before(before.stream().map(RoleSummary::code).toList())
                .after(after.stream().map(RoleSummary::code).toList())
                .build());
        return after;
    }

    /**
     * Initial roles of a staff member being created in the same transaction (no previous assignments or sessions).
     */
    @Transactional
    public List<RoleSummary> assignInitialRoles(UUID staffId, Set<UUID> roleIds) {
        return replaceAssignments(staffId, roleIds, CurrentActor.current().map(AuthenticatedActor::id).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<RoleSummary> rolesOf(UUID staffId) {
        return roleRepository.findRolesOfStaff(TenantContext.requireTenantId(), staffId).stream()
                .map(iamMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> effectivePermissionsOf(UUID staffId) {
        return new TreeSet<>(roleRepository.findEffectivePermissionCodes(TenantContext.requireTenantId(), staffId));
    }

    /**
     * Creates the default role templates for a new institution.
     *
     * @return role ids by role code
     */
    @Transactional
    public Map<String, UUID> provisionDefaultRoles() {
        UUID tenantId = TenantContext.requireTenantId();
        Map<String, UUID> ids = new LinkedHashMap<>();
        DefaultRoleCatalog.templates().values().forEach(template -> {
            Role role = roleRepository.save(new Role(UuidV7.next(), tenantId, template.code(), template.name(),
                    template.description(), true, validatedTenantPermissions(template.permissions())));
            ids.put(template.code(), role.getId());
        });
        roleRepository.flush();
        return ids;
    }

    private List<RoleSummary> replaceAssignments(UUID staffId, Set<UUID> roleIds, UUID assignedBy) {
        UUID tenantId = TenantContext.requireTenantId();
        List<Role> roles = roleIds.isEmpty() ? List.of() : roleRepository.findByTenantIdAndIdIn(tenantId, roleIds);
        if (roles.size() != roleIds.size()) {
            throw new ResourceNotFoundException("Role");
        }
        if (roles.stream().anyMatch(role -> !role.isActive())) {
            throw new BankingException(IamErrorCode.ROLE_INACTIVE);
        }
        staffRoleRepository.deleteAllOfStaff(tenantId, staffId);
        Instant now = clock.instant();
        roles.forEach(role -> staffRoleRepository.save(new StaffRole(tenantId, staffId, role.getId(), now,
                assignedBy)));
        staffRoleRepository.flush();
        return roles.stream()
                .sorted((a, b) -> a.getCode().compareTo(b.getCode()))
                .map(iamMapper::toSummary)
                .toList();
    }

    private Set<String> validatedTenantPermissions(Set<String> requested) {
        Set<String> codes = requested.stream().map(String::trim).collect(Collectors.toSet());
        Set<String> valid = permissionRepository.findByCodeIn(codes).stream()
                .filter(permission -> permission.getScope() == PermissionScope.TENANT)
                .map(Permission::getCode)
                .collect(Collectors.toSet());
        if (!valid.containsAll(codes)) {
            Set<String> invalid = new TreeSet<>(codes);
            invalid.removeAll(valid);
            throw new BankingException(IamErrorCode.INVALID_PERMISSION,
                    "Unknown or non-assignable permissions: " + String.join(", ", invalid));
        }
        return codes;
    }

    private Role load(UUID roleId) {
        return roleRepository.findByTenantIdAndId(TenantContext.requireTenantId(), roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role"));
    }
}
