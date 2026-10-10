package com.company.banking.channel.repository;

import com.company.banking.channel.entity.CustomerNotification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerNotificationRepository extends JpaRepository<CustomerNotification, UUID> {

    Optional<CustomerNotification> findByTenantIdAndId(UUID tenantId, UUID id);

    Page<CustomerNotification> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId,
                                                                               Pageable page);

    long countByTenantIdAndCustomerIdAndReadAtIsNull(UUID tenantId, UUID customerId);

    boolean existsByTenantIdAndCustomerIdAndSourceKey(UUID tenantId, UUID customerId, UUID sourceKey);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update CustomerNotification n set n.readAt = :now where n.tenantId = :tenantId"
            + " and n.customerId = :customerId and n.readAt is null")
    int markAllRead(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
                    @Param("now") Instant now);
}
