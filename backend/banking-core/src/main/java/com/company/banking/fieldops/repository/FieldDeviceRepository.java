package com.company.banking.fieldops.repository;

import com.company.banking.fieldops.entity.FieldDevice;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FieldDeviceRepository extends JpaRepository<FieldDevice, UUID> {

    Optional<FieldDevice> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Row lock taken for every item of a sync: one device's collections are processed one at a time.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from FieldDevice d where d.tenantId = :tenantId and d.id = :id")
    Optional<FieldDevice> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("select d from FieldDevice d where d.tenantId = :tenantId and d.deviceKey = :deviceKey"
            + " and d.revokedAt is null")
    Optional<FieldDevice> findLiveByKey(@Param("tenantId") UUID tenantId, @Param("deviceKey") String deviceKey);

    List<FieldDevice> findAllByTenantIdAndOfficerIdOrderByRegisteredAtDesc(UUID tenantId, UUID officerId);

    List<FieldDevice> findAllByTenantIdOrderByRegisteredAtDesc(UUID tenantId);
}
