package com.company.banking.account.repository;

import com.company.banking.account.entity.Account;
import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.model.HolderRole;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, UUID>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Account> findByTenantIdAndAccountNumber(UUID tenantId, String accountNumber);

    /**
     * Row lock for every lifecycle change, so status changes, closing and holds on one account serialise.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.tenantId = :tenantId and a.id = :id")
    Optional<Account> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    @Query("""
            select a from Account a
            where a.tenantId = :tenantId
              and a.id in (select h.id.accountId from AccountHolder h
                           where h.tenantId = :tenantId and h.id.customerId = :customerId and h.removedAt is null)
            order by a.openedOn, a.accountNumber""")
    List<Account> findHeldBy(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId);

    @Query("""
            select count(a) > 0 from Account a
            where a.tenantId = :tenantId and a.status <> :closed
              and a.id in (select h.id.accountId from AccountHolder h
                           where h.tenantId = :tenantId and h.id.customerId = :customerId
                             and h.removedAt is null and h.role in :owningRoles)""")
    boolean existsOpenOwnedBy(@Param("tenantId") UUID tenantId, @Param("customerId") UUID customerId,
                              @Param("closed") AccountStatus closed,
                              @Param("owningRoles") Collection<HolderRole> owningRoles);
}
