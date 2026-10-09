package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.FieldAlert;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldAlertRepository extends JpaRepository<FieldAlert, UUID> {

    Optional<FieldAlert> findByTenantIdAndId(UUID tenantId, UUID id);

    List<FieldAlert> findAllByTenantIdAndDeviceIdAndAlertTypeAndStatus(UUID tenantId, UUID deviceId,
                                                                       FieldAlert.Type alertType,
                                                                       FieldAlert.Status status);

    @Query("select count(a) from FieldAlert a where a.tenantId = :tenantId and a.officerId = :officerId"
            + " and a.status = com.company.banking.fieldops.entity.FieldAlert.Status.OPEN")
    long countOpenByOfficer(@Param("tenantId") UUID tenantId, @Param("officerId") UUID officerId);

    @Query("""
            select a from FieldAlert a
            where a.tenantId = :tenantId
              and a.officerId in :officerIds
              and (:status is null or a.status = :status)
            order by a.raisedAt desc, a.id desc""")
    Page<FieldAlert> search(@Param("tenantId") UUID tenantId, @Param("officerIds") Collection<UUID> officerIds,
                            @Param("status") FieldAlert.Status status, Pageable page);
}
