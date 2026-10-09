package com.company.banking.teller.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.BalanceSnapshot;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.teller.dto.CreateDrawerRequest;
import com.company.banking.teller.dto.CreateVaultRequest;
import com.company.banking.teller.dto.DrawerResponse;
import com.company.banking.teller.dto.VaultResponse;
import com.company.banking.teller.entity.CashDrawer;
import com.company.banking.teller.entity.TellerSession;
import com.company.banking.teller.entity.Vault;
import com.company.banking.teller.exception.TellerErrorCode;
import com.company.banking.teller.repository.CashDrawerRepository;
import com.company.banking.teller.repository.TellerSessionRepository;
import com.company.banking.teller.repository.VaultRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Vaults and teller drawers. Each is a balance-checked ledger account under the vault cash or branch cash GL, so the
 * cash it holds is its ledger balance and can never go below zero.
 */
@Service
@RequiredArgsConstructor
public class CashPointService {

    static final String VAULT_RESOURCE = "VAULT";
    static final String DRAWER_RESOURCE = "CASH_DRAWER";
    private static final List<TellerSession.Status> ACTIVE_SESSION = List.of(TellerSession.Status.OPEN,
            TellerSession.Status.BALANCING);

    private final VaultRepository vaults;
    private final CashDrawerRepository drawers;
    private final TellerSessionRepository sessions;
    private final LedgerAccountService ledgerAccounts;
    private final BranchService branchService;
    private final CurrencyService currencies;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public VaultResponse createVault(CreateVaultRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchResponse branch = activeBranchInScope(request.branchId());
        String currency = currencies.require(request.currency()).code();
        if (vaults.findByTenantIdAndBranchIdAndCurrencyAndStatus(tenantId, branch.id(), currency, Vault.Status.ACTIVE)
                .isPresent()) {
            throw new BankingException(TellerErrorCode.VAULT_EXISTS);
        }
        UUID id = UuidV7.next();
        UUID ledgerAccount = ledgerAccounts.open(new OpenLedgerAccountCommand(null, SystemAccount.VAULT_CASH,
                branch.id(), currency, LedgerAccountType.VAULT, true, "Vault " + branch.code() + " " + currency,
                "VAULT", id)).id();
        Vault vault = vaults.saveAndFlush(new Vault(id, tenantId, branch.id(), currency, request.name().trim(),
                ledgerAccount, clock.instant(), CurrentActor.currentActorId().orElse(null)));
        VaultResponse response = toResponse(vault, ledgerAccounts.balance(ledgerAccount));
        auditService.record(AuditEvent.builder("VAULT_CREATED", VAULT_RESOURCE)
                .resourceId(id)
                .branchId(branch.id())
                .after(response)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<VaultResponse> vaults() {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        List<Vault> visible = vaults.findAllByTenantIdOrderByBranchIdAscCurrencyAsc(tenantId).stream()
                .filter(vault -> scope.permits(vault.getBranchId()))
                .toList();
        Map<UUID, BalanceSnapshot> balances = ledgerAccounts.balances(visible.stream()
                .map(Vault::getLedgerAccountId).toList());
        return visible.stream().map(vault -> toResponse(vault, balances.get(vault.getLedgerAccountId()))).toList();
    }

    @Transactional
    public DrawerResponse createDrawer(CreateDrawerRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchResponse branch = activeBranchInScope(request.branchId());
        String currency = currencies.require(request.currency()).code();
        String code = request.code().trim();
        if (drawers.existsByTenantIdAndBranchIdAndCode(tenantId, branch.id(), code)) {
            throw new BankingException(TellerErrorCode.DRAWER_EXISTS);
        }
        UUID id = UuidV7.next();
        UUID ledgerAccount = ledgerAccounts.open(new OpenLedgerAccountCommand(null, SystemAccount.CASH_AT_BRANCH,
                branch.id(), currency, LedgerAccountType.TELLER_DRAWER, true,
                "Drawer " + branch.code() + " " + code + " " + currency, "DRAWER", id)).id();
        CashDrawer drawer = drawers.saveAndFlush(new CashDrawer(id, tenantId, branch.id(), currency, code,
                request.name().trim(), ledgerAccount, clock.instant(), CurrentActor.currentActorId().orElse(null)));
        DrawerResponse response = toResponse(drawer, ledgerAccounts.balance(ledgerAccount), null);
        auditService.record(AuditEvent.builder("CASH_DRAWER_CREATED", DRAWER_RESOURCE)
                .resourceId(id)
                .resourceReference(code)
                .branchId(branch.id())
                .after(response)
                .build());
        return response;
    }

    @Transactional(readOnly = true)
    public List<DrawerResponse> drawers(UUID branchId) {
        UUID tenantId = TenantContext.requireTenantId();
        BranchScope scope = CurrentActor.require().branchScope();
        List<CashDrawer> visible = drawers.findAllByTenantIdOrderByBranchIdAscCodeAsc(tenantId).stream()
                .filter(drawer -> scope.permits(drawer.getBranchId()))
                .filter(drawer -> branchId == null || drawer.getBranchId().equals(branchId))
                .toList();
        Map<UUID, BalanceSnapshot> balances = ledgerAccounts.balances(visible.stream()
                .map(CashDrawer::getLedgerAccountId).toList());
        Map<UUID, TellerSession> active = sessions.findAllByTenantIdAndStatusIn(tenantId, ACTIVE_SESSION).stream()
                .collect(Collectors.toMap(TellerSession::getCashDrawerId, Function.identity()));
        return visible.stream()
                .map(drawer -> toResponse(drawer, balances.get(drawer.getLedgerAccountId()), active.get(drawer.getId())))
                .toList();
    }

    // ----------------------------------------------------------------------------------- for the teller module

    CashDrawer drawerInScope(UUID drawerId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return drawers.findByTenantIdAndId(TenantContext.requireTenantId(), drawerId)
                .filter(drawer -> scope.permits(drawer.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer"));
    }

    CashDrawer drawer(UUID drawerId) {
        return drawers.findByTenantIdAndId(TenantContext.requireTenantId(), drawerId)
                .orElseThrow(() -> new ResourceNotFoundException("Cash drawer"));
    }

    Vault vaultOf(UUID branchId, String currency) {
        return vaults.findByTenantIdAndBranchIdAndCurrencyAndStatus(TenantContext.requireTenantId(), branchId, currency,
                        Vault.Status.ACTIVE)
                .orElseThrow(() -> new BankingException(TellerErrorCode.NO_VAULT));
    }

    Vault vault(UUID vaultId) {
        return vaults.findByTenantIdAndId(TenantContext.requireTenantId(), vaultId)
                .orElseThrow(() -> new ResourceNotFoundException("Vault"));
    }

    private BranchResponse activeBranchInScope(UUID branchId) {
        if (!CurrentActor.require().branchScope().permits(branchId)) {
            throw new ResourceNotFoundException("Branch");
        }
        BranchResponse branch = branchService.getForInternalUse(branchId);
        if (!branch.isActive()) {
            throw new BankingException(TellerErrorCode.DRAWER_NOT_AVAILABLE, "The branch is not active.");
        }
        return branch;
    }

    private VaultResponse toResponse(Vault vault, BalanceSnapshot balance) {
        return new VaultResponse(vault.getId(), vault.getBranchId(), vault.getCurrency(), vault.getName(),
                vault.getStatus().name(), balance == null ? null : balance.ledgerBalance(), vault.getVersion());
    }

    private DrawerResponse toResponse(CashDrawer drawer, BalanceSnapshot balance, TellerSession session) {
        return new DrawerResponse(drawer.getId(), drawer.getBranchId(), drawer.getCurrency(), drawer.getCode(),
                drawer.getName(), drawer.getStatus().name(), balance == null ? null : balance.ledgerBalance(),
                session == null ? null : session.getId(), session == null ? null : session.getTellerId(),
                drawer.getVersion());
    }
}
