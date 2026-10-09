package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanProductVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductVersionRepository extends JpaRepository<LoanProductVersion, UUID> {

    Optional<LoanProductVersion> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LoanProductVersion> findAllByTenantIdAndProductIdOrderByVersionNoDesc(UUID tenantId, UUID productId);

    Optional<LoanProductVersion> findByTenantIdAndProductIdAndStatus(UUID tenantId, UUID productId,
                                                                    LoanProductVersion.Status status);
}
