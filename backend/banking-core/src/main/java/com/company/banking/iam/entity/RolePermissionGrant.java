package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.util.UUID;

/**
 * Row of {@code core.role_permission}. Carries the tenant id so the table can use a composite tenant foreign key
 * and row-level security like every other tenant-owned table.
 */
@Embeddable
public record RolePermissionGrant(
        @Column(name = "tenant_id", nullable = false) UUID tenantId,
        @Column(name = "permission_code", nullable = false, length = 64) String permissionCode) {
}
