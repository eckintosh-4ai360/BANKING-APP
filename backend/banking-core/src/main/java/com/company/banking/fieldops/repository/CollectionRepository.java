package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.Collection;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CollectionRepository extends JpaRepository<Collection, UUID> {

    Optional<Collection> findByTenantIdAndClientReference(UUID tenantId, UUID clientReference);

    Optional<Collection> findByDeviceIdAndDeviceSequenceNo(UUID deviceId, long deviceSequenceNo);

    @Query("select coalesce(max(c.deviceSequenceNo), 0) from Collection c where c.deviceId = :deviceId")
    long highestSequenceNo(@Param("deviceId") UUID deviceId);

    @Query("""
            select c from Collection c
            where c.tenantId = :tenantId
              and c.officerId in :officerIds
              and (:customerId is null or c.customerId = :customerId)
              and (:status is null or c.status = :status)
              and c.collectedAt >= :from and c.collectedAt < :to
            order by c.collectedAt desc, c.id desc""")
    Page<Collection> search(@Param("tenantId") UUID tenantId, @Param("officerIds") Set<UUID> officerIds,
                            @Param("customerId") UUID customerId, @Param("status") Collection.Status status,
                            @Param("from") Instant from, @Param("to") Instant to, Pageable page);
}
