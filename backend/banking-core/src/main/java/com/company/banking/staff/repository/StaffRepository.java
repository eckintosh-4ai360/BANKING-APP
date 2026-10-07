package com.company.banking.staff.repository;

import com.company.banking.staff.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StaffRepository extends JpaRepository<Staff, UUID>, JpaSpecificationExecutor<Staff> {

    Optional<Staff> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndEmployeeNumber(UUID tenantId, String employeeNumber);

    @Query("select count(s) > 0 from Staff s where s.tenantId = :tenantId and lower(s.email) = lower(:email)")
    boolean emailTaken(@Param("tenantId") UUID tenantId, @Param("email") String email);

    @Query("""
            select count(s) > 0 from Staff s
            where s.tenantId = :tenantId and lower(s.email) = lower(:email) and s.id <> :staffId
            """)
    boolean emailTakenByOther(@Param("tenantId") UUID tenantId, @Param("email") String email,
                              @Param("staffId") UUID staffId);
}
