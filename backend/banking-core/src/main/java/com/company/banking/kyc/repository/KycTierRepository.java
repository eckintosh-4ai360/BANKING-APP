package com.company.banking.kyc.repository;

import com.company.banking.kyc.entity.KycTier;
import com.company.banking.kyc.entity.KycTierId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycTierRepository extends JpaRepository<KycTier, KycTierId> {

    List<KycTier> findByIdTenantIdOrderByTierRankAsc(UUID tenantId);

    boolean existsByIdTenantId(UUID tenantId);
}
