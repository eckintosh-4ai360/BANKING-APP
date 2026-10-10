package com.company.banking.channel.repository;

import com.company.banking.channel.entity.Beneficiary;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, UUID> {

    Optional<Beneficiary> findByTenantIdAndId(UUID tenantId, UUID id);

    @Query("select b from Beneficiary b where b.tenantId = :tenantId and b.customerId = :customerId"
            + " and b.status = com.company.banking.channel.entity.Beneficiary.Status.ACTIVE"
            + " order by b.favourite desc, b.nickname")
    List<Beneficiary> findActive(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

    @Query("select b from Beneficiary b where b.tenantId = :tenantId and b.customerId = :customerId"
            + " and b.accountId = :accountId"
            + " and b.status = com.company.banking.channel.entity.Beneficiary.Status.ACTIVE")
    Optional<Beneficiary> findActiveForAccount(@Param("tenantId") UUID tenantId,
                                               @Param("customerId") UUID customerId,
                                               @Param("accountId") UUID accountId);
}
