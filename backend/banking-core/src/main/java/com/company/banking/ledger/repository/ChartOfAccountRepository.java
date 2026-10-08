package com.company.banking.ledger.repository;

import com.company.banking.ledger.entity.ChartOfAccount;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {

    Optional<ChartOfAccount> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<ChartOfAccount> findByTenantIdAndSystemCode(UUID tenantId, String systemCode);

    Optional<ChartOfAccount> findByTenantIdAndCode(UUID tenantId, String code);

    List<ChartOfAccount> findAllByTenantIdOrderByCode(UUID tenantId);

    List<ChartOfAccount> findAllByTenantIdAndParentId(UUID tenantId, UUID parentId);

    boolean existsByTenantId(UUID tenantId);

    boolean existsByTenantIdAndCode(UUID tenantId, String code);
}
