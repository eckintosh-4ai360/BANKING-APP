package com.company.banking.iam.repository;

import com.company.banking.iam.entity.StaffCredential;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StaffCredentialRepository extends JpaRepository<StaffCredential, UUID> {

    /**
     * Row-locked so concurrent failed attempts are counted exactly.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StaffCredential c where c.tenantId = :tenantId and c.username = :username")
    Optional<StaffCredential> lockByUsername(@Param("tenantId") UUID tenantId, @Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StaffCredential c where c.tenantId = :tenantId and c.staffId = :staffId")
    Optional<StaffCredential> lockByStaffId(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);

    Optional<StaffCredential> findByTenantIdAndStaffId(UUID tenantId, UUID staffId);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);
}
