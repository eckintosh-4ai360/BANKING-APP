package com.company.banking.iam.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A tenant-defined collection of permissions.
 */
@Getter
@Entity
@Table(name = "role")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Role extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, updatable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "system_role", nullable = false, updatable = false)
    private boolean systemRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RoleStatus status;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permission", joinColumns = @JoinColumn(name = "role_id"))
    private Set<RolePermissionGrant> grants = new HashSet<>();

    public Role(UUID id, UUID tenantId, String code, String name, String description, boolean systemRole,
                Set<String> permissionCodes) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.description = description;
        this.systemRole = systemRole;
        this.status = RoleStatus.ACTIVE;
        replacePermissions(permissionCodes);
    }

    public void update(String name, String description, RoleStatus status, Set<String> permissionCodes) {
        this.name = name;
        this.description = description;
        this.status = status;
        replacePermissions(permissionCodes);
    }

    public Set<String> permissionCodes() {
        return grants.stream().map(RolePermissionGrant::permissionCode).collect(Collectors.toCollection(TreeSet::new));
    }

    public boolean isActive() {
        return status == RoleStatus.ACTIVE;
    }

    private void replacePermissions(Set<String> permissionCodes) {
        Set<RolePermissionGrant> target = permissionCodes.stream()
                .map(code -> new RolePermissionGrant(tenantId, code))
                .collect(Collectors.toSet());
        grants.retainAll(target);
        grants.addAll(target);
    }
}
