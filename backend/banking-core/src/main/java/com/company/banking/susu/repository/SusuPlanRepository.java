package com.company.banking.susu.repository;

import com.company.banking.susu.entity.SusuPlan;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SusuPlanRepository extends JpaRepository<SusuPlan, UUID> {

    Optional<SusuPlan> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Row lock for every change to a plan or its contributions, so a payment, end-of-day and a cancellation
     * serialise.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from SusuPlan p where p.tenantId = :tenantId and p.id = :id")
    Optional<SusuPlan> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    List<SusuPlan> findAllByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);

    @Query("""
            select p from SusuPlan p
            where p.tenantId = :tenantId
              and (:allBranches = true or p.branchId in :branchIds)
              and (:customerId is null or p.customerId = :customerId)
              and (:status is null or p.status = :status)
            order by p.createdAt desc, p.id desc""")
    Page<SusuPlan> search(@Param("tenantId") UUID tenantId, @Param("allBranches") boolean allBranches,
                          @Param("branchIds") Collection<UUID> branchIds, @Param("customerId") UUID customerId,
                          @Param("status") SusuPlan.Status status, Pageable page);

    /**
     * Next batch (by id) of running plans, for end-of-day.
     */
    @Query("select p.id from SusuPlan p where p.tenantId = :tenantId and p.status = :status and p.id > :after"
            + " order by p.id")
    List<UUID> idsWithStatus(@Param("tenantId") UUID tenantId, @Param("status") SusuPlan.Status status,
                             @Param("after") UUID after, Limit limit);
}
