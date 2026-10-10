package com.company.banking.channel.repository;

import com.company.banking.channel.entity.CustomerOnboarding;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerOnboardingRepository extends JpaRepository<CustomerOnboarding, UUID> {

    Optional<CustomerOnboarding> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    Optional<CustomerOnboarding> findByTenantIdAndKycCaseId(UUID tenantId, UUID kycCaseId);

    /** Row lock for submitting and opening the first account, so a double tap does either once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from CustomerOnboarding o where o.tenantId = :tenantId and o.customerId = :customerId")
    Optional<CustomerOnboarding> lock(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);
}
