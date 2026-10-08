package com.company.banking.ledger.service;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.LedgerAccountInfo;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.entity.LedgerAccount;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.NormalSide;
import com.company.banking.ledger.repository.BalanceRepository;
import com.company.banking.ledger.repository.BalanceRepository.BalanceRow;
import com.company.banking.ledger.repository.LedgerAccountRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sub-ledger accounts for other modules (customer accounts, drawers, collectors). Opening one also creates its
 * balance row (database trigger).
 */
@Service
@RequiredArgsConstructor
public class LedgerAccountService {

    private final LedgerAccountRepository repository;
    private final BalanceRepository balances;
    private final ChartOfAccountService chartOfAccounts;
    private final CurrencyService currencies;
    private final BranchService branchService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public LedgerAccountInfo open(OpenLedgerAccountCommand command) {
        UUID tenantId = TenantContext.requireTenantId();
        GlAccountRef gl = command.chartOfAccountId() != null
                ? chartOfAccounts.requirePostable(command.chartOfAccountId())
                : chartOfAccounts.requireSystem(command.systemAccount());
        currencies.require(command.currency());
        BranchResponse branch = branchService.getForInternalUse(command.branchId());
        if (!branch.isActive()) {
            throw new BankingException(LedgerErrorCode.BRANCH_NOT_ACTIVE);
        }
        LedgerAccount account = repository.saveAndFlush(new LedgerAccount(UuidV7.next(), tenantId, gl.id(),
                branch.id(), command.currency(), command.type(), NormalSide.valueOf(gl.normalSide()),
                command.balanceCheck(), command.name(), command.ownerType(), command.ownerId(), clock.instant(),
                CurrentActor.currentActorId().orElse(null)));
        return toInfo(account, gl.code());
    }

    @Transactional(readOnly = true)
    public LedgerAccountInfo get(UUID ledgerAccountId) {
        LedgerAccount account = load(ledgerAccountId);
        return toInfo(account, chartOfAccounts.get(account.getChartOfAccountId()).code());
    }

    @Transactional(readOnly = true)
    public BalanceSnapshot balance(UUID ledgerAccountId) {
        return balances.find(TenantContext.requireTenantId(), ledgerAccountId)
                .map(this::toSnapshot)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account"));
    }

    @Transactional(readOnly = true)
    public Map<UUID, BalanceSnapshot> balances(Collection<UUID> ledgerAccountIds) {
        return balances.find(TenantContext.requireTenantId(), ledgerAccountIds).stream()
                .map(this::toSnapshot)
                .collect(Collectors.toMap(BalanceSnapshot::ledgerAccountId, Function.identity()));
    }

    /**
     * Locks the balance row until the caller's transaction ends, so a decision based on it (e.g. placing a hold)
     * cannot race a posting.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public BalanceSnapshot lockBalance(UUID ledgerAccountId) {
        return balances.lockForUpdate(TenantContext.requireTenantId(), List.of(ledgerAccountId)).stream()
                .findFirst()
                .map(this::toSnapshot)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account"));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void setOverdraftLimit(UUID ledgerAccountId, BigDecimal limit) {
        LedgerAccount account = load(ledgerAccountId);
        if (limit.signum() < 0) {
            throw new BankingException(LedgerErrorCode.INVALID_AMOUNT, "The overdraft limit cannot be negative.");
        }
        if (limit.signum() > 0) {
            currencies.requireValidAmount(limit, account.getCurrency());
        }
        balances.setOverdraftLimit(account.getTenantId(), account.getId(), limit);
    }

    /**
     * Closes an account; the database refuses unless its balance and holds are zero.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void close(UUID ledgerAccountId) {
        LedgerAccount account = load(ledgerAccountId);
        BalanceSnapshot balance = balance(ledgerAccountId);
        if (balance.ledgerBalance().signum() != 0 || balance.holdAmount().signum() != 0) {
            throw new BankingException(LedgerErrorCode.INVALID_POSTING,
                    "Only an account with a zero balance and no holds can be closed.");
        }
        account.close(clock.instant());
        repository.saveAndFlush(account);
    }

    BalanceSnapshot toSnapshot(BalanceRow row) {
        return new BalanceSnapshot(row.ledgerAccountId(), row.currency(),
                currencies.present(row.ledgerBalance(), row.currency()),
                currencies.present(row.holdAmount(), row.currency()),
                currencies.present(row.availableBalance(), row.currency()),
                currencies.present(row.overdraftLimit(), row.currency()));
    }

    private LedgerAccount load(UUID ledgerAccountId) {
        return repository.findByTenantIdAndId(TenantContext.requireTenantId(), ledgerAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Ledger account"));
    }

    private static LedgerAccountInfo toInfo(LedgerAccount account, String glCode) {
        return new LedgerAccountInfo(account.getId(), account.getChartOfAccountId(), glCode, account.getBranchId(),
                account.getCurrency(), account.getType().name(), account.getNormalSide().name(),
                account.isBalanceCheck(), account.getName(), account.getOwnerType(), account.getOwnerId(),
                account.getStatus().name());
    }
}
