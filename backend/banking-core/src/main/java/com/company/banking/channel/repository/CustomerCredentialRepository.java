package com.company.banking.channel.repository;

import com.company.banking.channel.entity.CustomerCredential;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerCredentialRepository extends JpaRepository<CustomerCredential, UUID> {

    Optional<CustomerCredential> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    boolean existsByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    boolean existsByTenantIdAndUsername(UUID tenantId, String username);

    /** Row lock for sign-in, PIN checks and every change, so failed-attempt counters never lose an update. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerCredential c where c.tenantId = :tenantId and c.username = :username")
    Optional<CustomerCredential> lockByUsername(@Param("tenantId") UUID tenantId, @Param("username") String username);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CustomerCredential c where c.tenantId = :tenantId and c.customerId = :customerId")
    Optional<CustomerCredential> lockByCustomerId(@Param("tenantId") UUID tenantId,
                                                  @Param("customerId") UUID customerId);
}
