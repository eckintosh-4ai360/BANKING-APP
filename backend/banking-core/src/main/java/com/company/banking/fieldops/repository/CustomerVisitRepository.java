package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.CustomerVisit;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerVisitRepository extends JpaRepository<CustomerVisit, UUID> {

    Optional<CustomerVisit> findByTenantIdAndClientReference(UUID tenantId, UUID clientReference);

    @Query("""
            select v from CustomerVisit v
            where v.tenantId = :tenantId
              and v.officerId in :officerIds
              and (:customerId is null or v.customerId = :customerId)
            order by v.visitedAt desc, v.id desc""")
    Page<CustomerVisit> search(@Param("tenantId") UUID tenantId, @Param("officerIds") Collection<UUID> officerIds,
                               @Param("customerId") UUID customerId, Pageable page);
}
