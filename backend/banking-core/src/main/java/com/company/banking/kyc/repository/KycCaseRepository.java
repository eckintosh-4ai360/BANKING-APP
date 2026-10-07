package com.company.banking.kyc.repository;

import com.company.banking.kyc.entity.KycCase;
import com.company.banking.kyc.entity.KycCaseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface KycCaseRepository extends JpaRepository<KycCase, UUID>, JpaSpecificationExecutor<KycCase> {

    Optional<KycCase> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select k from KycCase k where k.tenantId = :tenantId and k.id = :id")
    Optional<KycCase> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    boolean existsByTenantIdAndCustomerIdAndStatusIn(UUID tenantId, UUID customerId,
                                                     Collection<KycCaseStatus> statuses);

    List<KycCase> findByTenantIdAndCustomerIdOrderByCreatedAtDesc(UUID tenantId, UUID customerId);
}
