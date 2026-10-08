package com.company.banking.account.service;

import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.model.HolderRole;
import com.company.banking.account.repository.AccountRepository;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.service.CustomerHoldingsCheck;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * A customer who owns (primary or joint) an account that is not closed cannot be closed. Signatories do not own the
 * account, so they do not block.
 */
@Component
@RequiredArgsConstructor
class AccountHoldingsCheck implements CustomerHoldingsCheck {

    private final AccountRepository accountRepository;

    @Override
    public boolean hasOpenHoldings(UUID customerId) {
        return accountRepository.existsOpenOwnedBy(TenantContext.requireTenantId(), customerId, AccountStatus.CLOSED,
                List.of(HolderRole.PRIMARY, HolderRole.JOINT));
    }
}
