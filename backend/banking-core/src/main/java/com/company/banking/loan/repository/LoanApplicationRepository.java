package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanApplication;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanApplicationRepository extends JpaRepository<LoanApplication, UUID> {

    Optional<LoanApplication> findByTenantIdAndId(UUID tenantId, UUID id);

    /** Row lock for every workflow step, so two steps on one application serialise. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from LoanApplication a where a.tenantId = :tenantId and a.id = :id")
    Optional<LoanApplication> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select a from LoanApplication a
            where a.tenantId = :tenantId
              and (:allBranches = true or a.branchId in :branchIds)
              and (:customerId is null or a.customerId = :customerId)
              and (:status is null or a.status = :status)
            order by a.createdAt desc, a.id desc""")
    Page<LoanApplication> search(@Param("tenantId") UUID tenantId, @Param("allBranches") boolean allBranches,
                                 @Param("branchIds") Collection<UUID> branchIds, @Param("customerId") UUID customerId,
                                 @Param("status") LoanApplication.Status status, Pageable page);
}
