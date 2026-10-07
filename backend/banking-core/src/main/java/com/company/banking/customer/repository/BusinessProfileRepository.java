package com.company.banking.customer.repository;

import com.company.banking.customer.entity.BusinessProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface BusinessProfileRepository extends JpaRepository<BusinessProfile, UUID> {

    Optional<BusinessProfile> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);

    @Query("""
            select count(b) > 0 from BusinessProfile b
            where b.tenantId = :tenantId and upper(b.registrationNumber) = upper(:registrationNumber)
              and b.customerId <> :customerId
            """)
    boolean registrationNumberTaken(@Param("tenantId") UUID tenantId,
                                    @Param("registrationNumber") String registrationNumber,
                                    @Param("customerId") UUID customerId);
}
