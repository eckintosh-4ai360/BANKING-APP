package com.company.banking.iam.repository;

import com.company.banking.iam.entity.StaffRole;
import com.company.banking.iam.entity.StaffRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StaffRoleRepository extends JpaRepository<StaffRole, StaffRoleId> {

    boolean existsByIdStaffIdAndIdRoleId(UUID staffId, UUID roleId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from StaffRole sr where sr.tenantId = :tenantId and sr.id.staffId = :staffId")
    int deleteAllOfStaff(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);
}
