package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.FieldOfficer;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldOfficerRepository extends JpaRepository<FieldOfficer, UUID> {

    Optional<FieldOfficer> findByTenantIdAndStaffId(UUID tenantId, UUID staffId);

    boolean existsByTenantIdAndStaffId(UUID tenantId, UUID staffId);

    List<FieldOfficer> findAllByTenantIdOrderByCreatedAtAsc(UUID tenantId);

    /**
     * Shared lock held while a collection posts, so the officer cannot be suspended half-way through a sync.
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select o from FieldOfficer o where o.tenantId = :tenantId and o.staffId = :staffId")
    Optional<FieldOfficer> lockShared(@Param("tenantId") UUID tenantId, @Param("staffId") UUID staffId);
}
