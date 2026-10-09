package com.company.banking.teller.repository;

import com.company.banking.teller.entity.CashDrawer;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashDrawerRepository extends JpaRepository<CashDrawer, UUID> {

    Optional<CashDrawer> findByTenantIdAndId(UUID tenantId, UUID id);

    boolean existsByTenantIdAndBranchIdAndCode(UUID tenantId, UUID branchId, String code);

    List<CashDrawer> findAllByTenantIdOrderByBranchIdAscCodeAsc(UUID tenantId);
}
