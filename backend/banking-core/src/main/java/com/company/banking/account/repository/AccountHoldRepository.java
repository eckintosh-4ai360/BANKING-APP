package com.company.banking.account.repository;

import com.company.banking.account.entity.AccountHold;
import com.company.banking.account.model.HoldStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountHoldRepository extends JpaRepository<AccountHold, UUID> {

    Optional<AccountHold> findByTenantIdAndIdAndAccountId(UUID tenantId, UUID id, UUID accountId);

    List<AccountHold> findAllByTenantIdAndAccountIdOrderByPlacedAtDesc(UUID tenantId, UUID accountId);

    List<AccountHold> findByTenantIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAt(UUID tenantId,
                                                                                      HoldStatus status,
                                                                                      Instant cutoff, Limit limit);
}
