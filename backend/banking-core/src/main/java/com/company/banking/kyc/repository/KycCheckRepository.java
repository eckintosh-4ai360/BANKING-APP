package com.company.banking.kyc.repository;

import com.company.banking.kyc.entity.KycCheck;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface KycCheckRepository extends JpaRepository<KycCheck, UUID> {

    List<KycCheck> findByTenantIdAndKycCaseIdOrderByPerformedAtAsc(UUID tenantId, UUID kycCaseId);
}
