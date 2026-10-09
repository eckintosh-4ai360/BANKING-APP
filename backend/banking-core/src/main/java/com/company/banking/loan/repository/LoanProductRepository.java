package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanProduct;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanProductRepository extends JpaRepository<LoanProduct, UUID> {

    Optional<LoanProduct> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);

    List<LoanProduct> findAllByTenantIdOrderByCode(UUID tenantId);
}
