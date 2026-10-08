package com.company.banking.account.service;

import com.company.banking.account.dto.HoldResponse;
import com.company.banking.account.dto.PlaceHoldRequest;
import com.company.banking.account.dto.ReleaseHoldRequest;
import com.company.banking.account.entity.Account;
import com.company.banking.account.entity.AccountHold;
import com.company.banking.account.exception.AccountErrorCode;
import com.company.banking.account.model.AccountStatus;
import com.company.banking.account.model.HoldStatus;
import com.company.banking.account.model.HoldType;
import com.company.banking.account.repository.AccountHoldRepository;
import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Holds (liens, legal and fraud-review holds) reserve funds without moving them. The database adds active holds into
 * the balance row, so the available balance and the no-overdraft rule account for them everywhere.
 */
@Service
@RequiredArgsConstructor
public class AccountHoldService {

    private static final String RESOURCE = "ACCOUNT_HOLD";

    private final AccountHoldRepository holdRepository;
    private final AccountAccessGuard accessGuard;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<HoldResponse> list(UUID accountId) {
        Account account = accessGuard.loadForRead(accountId);
        return holdRepository.findAllByTenantIdAndAccountIdOrderByPlacedAtDesc(account.getTenantId(), accountId)
                .stream()
                .map(hold -> toResponse(hold, account.getCurrency()))
                .toList();
    }

    /**
     * Places a hold no larger than what the account could pay out right now. The account and balance rows stay
     * locked until commit, so a concurrent withdrawal cannot spend the same funds.
     */
    @Transactional
    public HoldResponse place(UUID accountId, PlaceHoldRequest request) {
        Account account = accessGuard.lockInScope(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new BankingException(AccountErrorCode.ACCOUNT_CLOSED);
        }
        BigDecimal amount = request.amount();
        currencies.requireValidAmount(amount, account.getCurrency());
        BalanceSnapshot balance = ledgerAccounts.lockBalance(account.getLedgerAccountId());
        if (amount.compareTo(balance.availableBalance().add(balance.overdraftLimit())) > 0) {
            throw new BankingException(AccountErrorCode.INSUFFICIENT_FUNDS_FOR_HOLD);
        }
        Instant now = clock.instant();
        AccountHold hold = holdRepository.saveAndFlush(new AccountHold(UuidV7.next(), account.getTenantId(),
                accountId, account.getLedgerAccountId(), amount, HoldType.valueOf(request.holdType()),
                request.reason().trim(), blankToNull(request.reference()), request.expiresAt(), now,
                CurrentActor.currentActorId().orElse(null)));
        HoldResponse response = toResponse(hold, account.getCurrency());
        auditService.record(AuditEvent.builder("ACCOUNT_HOLD_PLACED", RESOURCE)
                .resourceId(hold.getId())
                .resourceReference(account.getAccountNumber())
                .branchId(account.getBranchId())
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public HoldResponse release(UUID accountId, UUID holdId, ReleaseHoldRequest request) {
        Account account = accessGuard.lockInScope(accountId);
        AccountHold hold = holdRepository.findByTenantIdAndIdAndAccountId(account.getTenantId(), holdId, accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Hold"));
        ConcurrentModificationException.assertVersion(request.version(), hold.getVersion());
        HoldResponse before = toResponse(hold, account.getCurrency());
        hold.end(HoldStatus.RELEASED, request.reason().trim(), clock.instant(),
                CurrentActor.currentActorId().orElse(null));
        HoldResponse after = toResponse(holdRepository.saveAndFlush(hold), account.getCurrency());
        auditService.record(AuditEvent.builder("ACCOUNT_HOLD_RELEASED", RESOURCE)
                .resourceId(holdId)
                .resourceReference(account.getAccountNumber())
                .branchId(account.getBranchId())
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * Ends holds whose expiry has passed (maintenance job, one tenant at a time). Returns how many expired.
     */
    @Transactional
    public int expireDue(int batchSize) {
        UUID tenantId = TenantContext.requireTenantId();
        Instant now = clock.instant();
        List<AccountHold> due = holdRepository.findByTenantIdAndStatusAndExpiresAtLessThanEqualOrderByExpiresAt(
                tenantId, HoldStatus.ACTIVE, now, Limit.of(batchSize));
        for (AccountHold hold : due) {
            hold.end(HoldStatus.EXPIRED, "Expired", now, null);
            auditService.record(AuditEvent.builder("ACCOUNT_HOLD_EXPIRED", RESOURCE)
                    .resourceId(hold.getId())
                    .metadata("accountId", hold.getAccountId())
                    .build());
        }
        holdRepository.saveAllAndFlush(due);
        return due.size();
    }

    private HoldResponse toResponse(AccountHold hold, String currency) {
        return new HoldResponse(hold.getId(), hold.getAccountId(), currencies.present(hold.getAmount(), currency),
                currency, hold.getHoldType().name(), hold.getStatus().name(), hold.getReason(), hold.getReference(),
                hold.getExpiresAt(), hold.getPlacedAt(), hold.getPlacedBy(), hold.getReleasedAt(),
                hold.getReleasedBy(), hold.getReleaseReason(), hold.getVersion());
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
