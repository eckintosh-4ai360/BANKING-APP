package com.company.banking.teller.repository;

import com.company.banking.teller.entity.Vault;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VaultRepository extends JpaRepository<Vault, UUID> {

    Optional<Vault> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Vault> findByTenantIdAndBranchIdAndCurrencyAndStatus(UUID tenantId, UUID branchId, String currency,
                                                                   Vault.Status status);

    List<Vault> findAllByTenantIdOrderByBranchIdAscCurrencyAsc(UUID tenantId);
}
