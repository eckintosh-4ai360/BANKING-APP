package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.CollectorRemittance;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectorRemittanceRepository extends JpaRepository<CollectorRemittance, UUID> {

    Optional<CollectorRemittance> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("""
            select r from CollectorRemittance r
            where r.tenantId = :tenantId and r.officerId in :officerIds
            order by r.createdAt desc, r.id desc""")
    Page<CollectorRemittance> search(@Param("tenantId") UUID tenantId, @Param("officerIds") Set<UUID> officerIds,
                                     Pageable page);
}
