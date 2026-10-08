package com.company.banking.ledger.repository;

import com.company.banking.ledger.entity.LedgerAccount;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerAccountRepository extends JpaRepository<LedgerAccount, UUID> {

    Optional<LedgerAccount> findByTenantIdAndId(UUID tenantId, UUID id);

    List<LedgerAccount> findAllByTenantIdAndIdIn(UUID tenantId, Collection<UUID> ids);

    Optional<LedgerAccount> findByTenantIdAndOwnerTypeAndOwnerId(UUID tenantId, String ownerType, UUID ownerId);
}
