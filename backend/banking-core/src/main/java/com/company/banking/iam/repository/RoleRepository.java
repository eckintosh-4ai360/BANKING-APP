package com.company.banking.iam.repository;

import com.company.banking.iam.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<Role> findByTenantIdOrderByCode(UUID tenantId);

    List<Role> findByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    /**
     * Effective permissions of a staff member: union over the staff member's active roles.
     */
    @Query("""
            select distinct g.permissionCode
            from Role r join r.grants g
            where r.tenantId = :tenantId
              and r.status = com.company.banking.iam.entity.RoleStatus.ACTIVE
              and r.id in (select sr.id.roleId from StaffRole sr where sr.id.staffId = :staffId)
            """)
    Set<String> findEffectivePermissionCodes(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);

    @Query("""
            select r from Role r
            where r.tenantId = :tenantId
              and r.id in (select sr.id.roleId from StaffRole sr where sr.id.staffId = :staffId)
            order by r.code
            """)
    List<Role> findRolesOfStaff(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);
}
