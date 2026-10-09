package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanCollateral;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanCollateralRepository extends JpaRepository<LoanCollateral, UUID> {

    Optional<LoanCollateral> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LoanCollateral> findAllByTenantIdAndApplicationIdOrderByCreatedAtAsc(UUID tenantId, UUID applicationId);
}
