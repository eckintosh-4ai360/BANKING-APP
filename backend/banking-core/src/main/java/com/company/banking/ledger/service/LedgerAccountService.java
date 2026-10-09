package com.company.banking.ledger.service;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.LedgerAccountInfo;
import com.company.banking.ledger.dto.LedgerAccountStatement;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.entity.LedgerAccount;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.EntryDirection;
import com.company.banking.ledger.model.NormalSide;
import com.company.banking.ledger.repository.BalanceRepository;
import com.company.banking.ledger.repository.BalanceRepository.BalanceRow;
import com.company.banking.ledger.repository.LedgerAccountRepository;
import com.company.banking.ledger.repository.LedgerReportRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
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
    private final LedgerReportRepository reports;
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

    /**
     * The account's entries between two business dates with running balances (for statements). The caller checks
     * that the reader may see the account.
     *
     * @param maxLines refuses periods with more entries than this, so a statement stays a reasonable size
     */
    @Transactional(readOnly = true)
    public LedgerAccountStatement statement(UUID ledgerAccountId, LocalDate from, LocalDate to, int maxLines) {
        UUID tenantId = TenantContext.requireTenantId();
        LedgerAccount account = load(ledgerAccountId);
        EntryDirection raises = account.getNormalSide().increasingDirection();
        LedgerReportRepository.GlTotals before = reports.accountTotalsBefore(tenantId, ledgerAccountId, from);
        BigDecimal balance = raises == EntryDirection.CREDIT ? before.credits().subtract(before.debits())
                : before.debits().subtract(before.credits());
        BigDecimal opening = balance;
        List<LedgerReportRepository.StatementEntry> entries = reports.accountEntries(tenantId, ledgerAccountId, from,
                to, maxLines + 1);
        if (entries.size() > maxLines) {
            throw new BankingException(CommonErrorCode.VALIDATION_FAILED,
                    "The period has more than " + maxLines + " entries. Choose a shorter period.");
        }
        String currency = account.getCurrency();
        BigDecimal debits = BigDecimal.ZERO;
        BigDecimal credits = BigDecimal.ZERO;
        List<LedgerAccountStatement.Line> lines = new ArrayList<>(entries.size());
        for (LedgerReportRepository.StatementEntry entry : entries) {
            boolean debit = EntryDirection.fromCode(entry.direction()) == EntryDirection.DEBIT;
            if (debit) {
                debits = debits.add(entry.amount());
            } else {
                credits = credits.add(entry.amount());
            }
            boolean raising = (debit ? EntryDirection.DEBIT : EntryDirection.CREDIT) == raises;
            balance = raising ? balance.add(entry.amount()) : balance.subtract(entry.amount());
            lines.add(new LedgerAccountStatement.Line(entry.businessDate(), entry.valueDate(), entry.postedAt(),
                    entry.journalNumber(), entry.sourceType(), entry.sourceReference(), entry.narration(),
                    debit ? currencies.present(entry.amount(), currency) : null,
                    debit ? null : currencies.present(entry.amount(), currency),
                    currencies.present(balance, currency)));
        }
        return new LedgerAccountStatement(ledgerAccountId, currency, from, to, currencies.present(opening, currency),
                currencies.present(debits, currency), currencies.present(credits, currency),
                currencies.present(balance, currency), List.copyOf(lines));
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
