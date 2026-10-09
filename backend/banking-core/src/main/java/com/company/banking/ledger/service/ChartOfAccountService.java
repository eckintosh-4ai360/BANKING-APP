package com.company.banking.ledger.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.ConcurrentModificationException;
import com.company.banking.common.error.DuplicateResourceException;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.ledger.dto.ChartOfAccountResponse;
import com.company.banking.ledger.dto.CreateChartOfAccountRequest;
import com.company.banking.ledger.dto.GlAccountRef;
import com.company.banking.ledger.dto.UpdateChartOfAccountRequest;
import com.company.banking.ledger.entity.ChartOfAccount;
import com.company.banking.ledger.entity.GlStatus;
import com.company.banking.ledger.exception.LedgerErrorCode;
import com.company.banking.ledger.model.AccountClass;
import com.company.banking.ledger.model.NormalSide;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.repository.ChartOfAccountRepository;
import com.company.banking.ledger.repository.LedgerReportRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ChartOfAccountService {

    private static final String RESOURCE = "CHART_OF_ACCOUNT";

    private final ChartOfAccountRepository repository;
    private final LedgerReportRepository reports;
    private final AuditService auditService;

    /**
     * Creates the default chart for a new institution. Idempotent: an institution that already has a chart keeps it.
     */
    @Transactional
    public void provisionDefaults(String institutionType) {
        UUID tenantId = TenantContext.requireTenantId();
        if (repository.existsByTenantId(tenantId)) {
            return;
        }
        Map<String, UUID> ids = new HashMap<>();
        List<DefaultChartOfAccounts.Entry> template = DefaultChartOfAccounts.forInstitutionType(institutionType);
        for (DefaultChartOfAccounts.Entry entry : template) {
            UUID id = UuidV7.next();
            repository.save(new ChartOfAccount(id, tenantId, entry.code(), entry.name(), entry.accountClass(),
                    entry.side(), entry.parentCode() == null ? null : ids.get(entry.parentCode()), entry.header(),
                    entry.manualPostingAllowed(),
                    entry.systemAccount() == null ? null : entry.systemAccount().name()));
            ids.put(entry.code(), id);
        }
        repository.flush();
        auditService.record(AuditEvent.builder("CHART_OF_ACCOUNTS_PROVISIONED", RESOURCE)
                .metadata("institutionType", institutionType)
                .metadata("accounts", template.size())
                .build());
    }

    /**
     * Adds system accounts introduced after an institution's chart was created (e.g. cash in transit), under their
     * default parent and code. An account whose code the institution already uses for something else is left out;
     * postings that need it then fail with {@code SYSTEM_ACCOUNT_MISSING} until it is set up.
     */
    @Transactional
    public void provisionMissingSystemAccounts(String institutionType) {
        UUID tenantId = TenantContext.requireTenantId();
        if (!repository.existsByTenantId(tenantId)) {
            return;
        }
        Map<String, ChartOfAccount> byCode = new HashMap<>();
        Set<String> systemCodes = new HashSet<>();
        for (ChartOfAccount account : repository.findAllByTenantIdOrderByCode(tenantId)) {
            byCode.put(account.getCode(), account);
            if (account.getSystemCode() != null) {
                systemCodes.add(account.getSystemCode());
            }
        }
        List<String> added = new ArrayList<>();
        for (DefaultChartOfAccounts.Entry entry : DefaultChartOfAccounts.forInstitutionType(institutionType)) {
            if (entry.systemAccount() == null || systemCodes.contains(entry.systemAccount().name())
                    || byCode.containsKey(entry.code())) {
                continue;
            }
            ChartOfAccount parent = entry.parentCode() == null ? null : byCode.get(entry.parentCode());
            if (entry.parentCode() != null && (parent == null || !parent.isHeader())) {
                continue;
            }
            ChartOfAccount account = repository.save(new ChartOfAccount(UuidV7.next(), tenantId, entry.code(),
                    entry.name(), entry.accountClass(), entry.side(), parent == null ? null : parent.getId(),
                    entry.header(), entry.manualPostingAllowed(), entry.systemAccount().name()));
            byCode.put(entry.code(), account);
            added.add(entry.code());
        }
        if (!added.isEmpty()) {
            repository.flush();
            auditService.record(AuditEvent.builder("SYSTEM_GL_ACCOUNTS_ADDED", RESOURCE)
                    .metadata("codes", added)
                    .build());
        }
    }

    @Transactional(readOnly = true)
    public List<ChartOfAccountResponse> list() {
        return repository.findAllByTenantIdOrderByCode(TenantContext.requireTenantId()).stream()
                .map(ChartOfAccountService::toResponse)
                .toList();
    }

    @Transactional
    public ChartOfAccountResponse create(CreateChartOfAccountRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        String code = request.code().trim();
        if (repository.existsByTenantIdAndCode(tenantId, code)) {
            throw new DuplicateResourceException("A GL account with this code already exists.");
        }
        AccountClass accountClass = AccountClass.valueOf(request.accountClass());
        ChartOfAccount parent = load(tenantId, request.parentId());
        if (!parent.isHeader() || parent.getStatus() != GlStatus.ACTIVE || parent.getAccountClass() != accountClass) {
            throw new BankingException(LedgerErrorCode.INVALID_PARENT_ACCOUNT);
        }
        NormalSide side = request.normalSide() == null ? accountClass.defaultSide()
                : NormalSide.valueOf(request.normalSide());
        ChartOfAccount account = repository.saveAndFlush(new ChartOfAccount(UuidV7.next(), tenantId, code,
                request.name().trim(), accountClass, side, parent.getId(), request.header(),
                request.manualPostingAllowed(), null));
        ChartOfAccountResponse response = toResponse(account);
        auditService.record(AuditEvent.builder("CHART_ACCOUNT_CREATED", RESOURCE)
                .resourceId(account.getId())
                .resourceReference(code)
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public ChartOfAccountResponse update(UUID id, UpdateChartOfAccountRequest request) {
        UUID tenantId = TenantContext.requireTenantId();
        ChartOfAccount account = load(tenantId, id);
        ConcurrentModificationException.assertVersion(account.getVersion(), request.version());
        ChartOfAccountResponse before = toResponse(account);
        GlStatus status = GlStatus.valueOf(request.status());
        if (status != account.getStatus()) {
            if (status == GlStatus.INACTIVE) {
                assertCanDeactivate(tenantId, account);
            } else if (account.getParentId() != null
                    && load(tenantId, account.getParentId()).getStatus() != GlStatus.ACTIVE) {
                throw new BankingException(LedgerErrorCode.INVALID_PARENT_ACCOUNT);
            }
            account.changeStatus(status);
        }
        account.update(request.name().trim(), request.manualPostingAllowed());
        ChartOfAccountResponse after = toResponse(repository.saveAndFlush(account));
        auditService.record(AuditEvent.builder("CHART_ACCOUNT_UPDATED", RESOURCE)
                .resourceId(account.getId())
                .resourceReference(account.getCode())
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * A GL account that postings may use, by id.
     */
    @Transactional(readOnly = true)
    public GlAccountRef requirePostable(UUID id) {
        ChartOfAccount account = repository.findByTenantIdAndId(TenantContext.requireTenantId(), id)
                .orElseThrow(() -> new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_POSTABLE,
                        "The GL account does not exist."));
        if (!account.isPostable()) {
            throw new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_POSTABLE);
        }
        return toRef(account);
    }

    /**
     * The institution's GL account for a system role (e.g. the inter-branch accounts).
     */
    @Transactional(readOnly = true)
    public GlAccountRef requireSystem(SystemAccount systemAccount) {
        ChartOfAccount account = repository.findByTenantIdAndSystemCode(TenantContext.requireTenantId(),
                        systemAccount.name())
                .orElseThrow(() -> new BankingException(LedgerErrorCode.SYSTEM_ACCOUNT_MISSING,
                        "The chart of accounts has no " + systemAccount + " account."));
        if (!account.isPostable()) {
            throw new BankingException(LedgerErrorCode.GL_ACCOUNT_NOT_POSTABLE);
        }
        return toRef(account);
    }

    @Transactional(readOnly = true)
    public GlAccountRef get(UUID id) {
        return toRef(load(TenantContext.requireTenantId(), id));
    }

    private void assertCanDeactivate(UUID tenantId, ChartOfAccount account) {
        if (account.getSystemCode() != null) {
            throw new BankingException(LedgerErrorCode.SYSTEM_GL_ACCOUNT_PROTECTED);
        }
        boolean activeChildren = repository.findAllByTenantIdAndParentId(tenantId, account.getId()).stream()
                .anyMatch(child -> child.getStatus() == GlStatus.ACTIVE);
        if (activeChildren
                || reports.glNetBalance(tenantId, account.getId()).compareTo(BigDecimal.ZERO) != 0
                || reports.hasActiveLedgerAccounts(tenantId, account.getId())) {
            throw new BankingException(LedgerErrorCode.GL_ACCOUNT_IN_USE);
        }
    }

    private ChartOfAccount load(UUID tenantId, UUID id) {
        return repository.findByTenantIdAndId(tenantId, id)
                .orElseThrow(() -> new ResourceNotFoundException("GL account"));
    }

    static ChartOfAccountResponse toResponse(ChartOfAccount account) {
        return new ChartOfAccountResponse(account.getId(), account.getCode(), account.getName(),
                account.getAccountClass().name(), account.getNormalSide().name(), account.getParentId(),
                account.isHeader(), account.isManualPostingAllowed(), account.getSystemCode(),
                account.getStatus().name(), account.getVersion());
    }

    private static GlAccountRef toRef(ChartOfAccount account) {
        return new GlAccountRef(account.getId(), account.getCode(), account.getName(),
                account.getAccountClass().name(), account.getNormalSide().name(), account.isHeader(),
                account.isManualPostingAllowed(), account.getSystemCode(), account.getStatus().name());
    }
}
