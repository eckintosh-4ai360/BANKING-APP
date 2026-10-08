package com.company.banking.account.repository;

import com.company.banking.account.entity.AccountHolder;
import com.company.banking.account.entity.AccountHolderId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountHolderRepository extends JpaRepository<AccountHolder, AccountHolderId> {

    @Query("""
            select h from AccountHolder h
            where h.tenantId = :tenantId and h.id.accountId = :accountId and h.removedAt is null
            order by h.addedAt""")
    List<AccountHolder> findCurrent(@Param("tenantId") UUID tenantId, @Param("accountId") UUID accountId);
}
