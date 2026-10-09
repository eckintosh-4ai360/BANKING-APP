package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.CustomerAssignment;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerAssignmentRepository extends JpaRepository<CustomerAssignment, UUID> {

    Optional<CustomerAssignment> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * The customer's open assignment, locked so a reassignment and a collection for the customer serialise.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerAssignment a where a.tenantId = :tenantId and a.customerId = :customerId"
            + " and a.endedAt is null")
    Optional<CustomerAssignment> lockActiveByCustomer(@Param("tenantId") UUID tenantId,
                                                      @Param("customerId") UUID customerId);

    @Query("select a from CustomerAssignment a where a.tenantId = :tenantId and a.customerId = :customerId"
            + " and a.endedAt is null")
    Optional<CustomerAssignment> findActiveByCustomer(@Param("tenantId") UUID tenantId,
                                                      @Param("customerId") UUID customerId);

    @Query("select a from CustomerAssignment a where a.tenantId = :tenantId and a.officerId = :officerId"
            + " and a.endedAt is null order by a.assignedAt")
    List<CustomerAssignment> findActiveByOfficer(@Param("tenantId") UUID tenantId, @Param("officerId") UUID officerId);

    @Query("select count(a) from CustomerAssignment a where a.tenantId = :tenantId and a.officerId = :officerId"
            + " and a.endedAt is null")
    long countActiveByOfficer(@Param("tenantId") UUID tenantId, @Param("officerId") UUID officerId);

    @Query("""
            select a from CustomerAssignment a
            where a.tenantId = :tenantId
              and (:officerId is null or a.officerId = :officerId)
              and (:customerId is null or a.customerId = :customerId)
              and (:activeOnly = false or a.endedAt is null)
            order by a.assignedAt desc""")
    Page<CustomerAssignment> search(@Param("tenantId") UUID tenantId, @Param("officerId") UUID officerId,
                                    @Param("customerId") UUID customerId, @Param("activeOnly") boolean activeOnly,
                                    Pageable page);
}
