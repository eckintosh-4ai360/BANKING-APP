package com.company.banking.account.service;

import com.company.banking.account.entity.Account;
import com.company.banking.account.repository.AccountRepository;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Accounts outside the caller's branch scope (by servicing branch) do not exist for the caller (404). Every change
 * locks the account row, so lifecycle changes and holds on one account serialise.
 */
@Component
@RequiredArgsConstructor
class AccountAccessGuard {

    private final AccountRepository accountRepository;

    Account loadForRead(UUID accountId) {
        return inScope(accountRepository.findByTenantIdAndId(TenantContext.requireTenantId(), accountId));
    }

    Account lockInScope(UUID accountId) {
        return inScope(accountRepository.lockByTenantIdAndId(TenantContext.requireTenantId(), accountId));
    }

    private static Account inScope(Optional<Account> account) {
        BranchScope scope = CurrentActor.require().branchScope();
        return account.filter(found -> scope.permits(found.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Account"));
    }
}
