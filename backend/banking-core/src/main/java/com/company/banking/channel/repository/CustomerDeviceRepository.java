package com.company.banking.channel.repository;

import com.company.banking.channel.entity.CustomerDevice;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerDeviceRepository extends JpaRepository<CustomerDevice, UUID> {

    Optional<CustomerDevice> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("select d from CustomerDevice d where d.tenantId = :tenantId and d.customerId = :customerId"
            + " and d.deviceKey = :deviceKey"
            + " and d.status = com.company.banking.channel.entity.CustomerDevice.Status.ACTIVE")
    Optional<CustomerDevice> findTrusted(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
                                         @Param("deviceKey") String deviceKey);

    List<CustomerDevice> findAllByTenantIdAndCustomerIdOrderByBoundAtDesc(UUID tenantId, UUID customerId);
}
