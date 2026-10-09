package com.company.banking.platform.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.dto.NewHeadOffice;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.customer.service.IdentificationTypeService;
import com.company.banking.iam.service.DefaultRoleCatalog;
import com.company.banking.kyc.service.KycTierService;
import com.company.banking.ledger.service.AccountingPeriodService;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.operations.service.BusinessCalendarService;
import com.company.banking.ledger.service.ChartOfAccountService;
import com.company.banking.iam.service.RoleService;
import com.company.banking.iam.service.SessionService;
import com.company.banking.platform.dto.ChangeTenantStatusRequest;
import com.company.banking.platform.dto.OnboardTenantRequest;
import com.company.banking.platform.dto.OnboardTenantResponse;
import com.company.banking.platform.dto.TenantDetailsResponse;
import com.company.banking.staff.dto.NewAdministrator;
import com.company.banking.staff.dto.StaffCreatedResponse;
import com.company.banking.staff.service.StaffService;
import com.company.banking.tenant.dto.FeatureResponse;
import com.company.banking.tenant.dto.FeatureStateResponse;
import com.company.banking.tenant.dto.NewTenantCommand;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.exception.TenantErrorCode;
import com.company.banking.tenant.service.TenantProvisioningService;
import com.company.banking.tenant.service.TenantService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Platform operations on institutions. Runs in platform context; it enters a tenant's context only to provision
 * or license that tenant, and never reads customer or financial data.
 */
@Service
@RequiredArgsConstructor
public class PlatformTenantService {

    private static final String RESOURCE = "TENANT";

    private final TenantService tenantService;
    private final TenantProvisioningService provisioningService;
    private final BranchService branchService;
    private final RoleService roleService;
    private final StaffService staffService;
    private final SessionService sessionService;
    private final IdentificationTypeService identificationTypeService;
    private final KycTierService kycTierService;
    private final ChartOfAccountService chartOfAccountService;
    private final AccountingPeriodService accountingPeriodService;
    private final BusinessDateService businessDateService;
    private final BusinessCalendarService businessCalendarService;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    /**
     * Creates the tenant, its profile and licences, the default roles, the head office and the first administrator
     * in one transaction, then activates it.
     */
    public OnboardTenantResponse onboard(OnboardTenantRequest request) {
        String code = request.code().trim().toLowerCase(Locale.ROOT);
        if (tenantService.findByCode(code).isPresent()) {
            throw new BankingException(TenantErrorCode.TENANT_CODE_TAKEN);
        }
        UUID tenantId = UuidV7.next();
        Set<String> features = request.features().stream()
                .map(feature -> feature.trim().toUpperCase(Locale.ROOT))
                .collect(Collectors.toSet());
        OnboardTenantResponse response = TenantContext.callAs(tenantId, () -> transactionTemplate.execute(status -> {
            provisioningService.createTenant(new NewTenantCommand(tenantId, code, request.legalName().trim(),
                    request.displayName().trim(), request.institutionType(), request.countryCode(),
                    request.baseCurrency(), request.timezone(), request.locale(), request.licenceNumber(),
                    request.contactEmail(), request.contactPhone(), features));
            BranchResponse headOffice = branchService.provisionHeadOffice(new NewHeadOffice(
                    request.headOffice().code(), request.headOffice().name(), request.headOffice().city(),
                    request.headOffice().region(), request.headOffice().digitalAddress()));
            Map<String, UUID> roles = roleService.provisionDefaultRoles();
            identificationTypeService.provisionDefaults(request.countryCode());
            kycTierService.provisionDefaults();
            chartOfAccountService.provisionDefaults(request.institutionType());
            businessDateService.provision();
            businessCalendarService.provisionDefaults();
            accountingPeriodService.provisionCurrentPeriod();
            OnboardTenantRequest.Administrator admin = request.administrator();
            StaffCreatedResponse administrator = staffService.provisionAdministrator(new NewAdministrator(
                    admin.firstName(), admin.lastName(), admin.email(), admin.phone(), admin.username()),
                    headOffice.id(), roles.get(DefaultRoleCatalog.INSTITUTION_ADMIN));
            TenantSummary activated = provisioningService.changeStatus(tenantId, "ACTIVE");
            auditService.record(AuditEvent.builder("TENANT_ONBOARDED", RESOURCE)
                    .resourceId(tenantId)
                    .resourceReference(code)
                    .after(Map.of("tenant", activated, "features", features.stream().sorted().toList()))
                    .build());
            return new OnboardTenantResponse(activated, headOffice, administrator.staff().id(),
                    administrator.credential());
        }));
        recordPlatformEvent("TENANT_ONBOARDED", tenantId, code, null, Map.of("status", "ACTIVE"), null);
        return response;
    }

    public PageResponse<TenantSummary> list(Integer page, Integer size) {
        PageRequest pageRequest = PageRequests.of(page, size, Sort.by("code"));
        return PageResponse.from(provisioningService.list(pageRequest), tenant -> tenant);
    }

    public TenantDetailsResponse get(UUID tenantId) {
        TenantSummary tenant = provisioningService.get(tenantId);
        List<FeatureStateResponse> features = TenantContext.callAs(tenantId,
                () -> provisioningService.featureStates(tenantId));
        return new TenantDetailsResponse(tenant, features);
    }

    /**
     * Suspension and termination also end every session of the institution's staff.
     */
    public TenantSummary changeStatus(UUID tenantId, ChangeTenantStatusRequest request) {
        TenantSummary before = provisioningService.get(tenantId);
        String target = request.status();
        TenantSummary after = provisioningService.changeStatus(tenantId, target);
        if (!"ACTIVE".equals(target)) {
            TenantContext.runAs(tenantId,
                    () -> sessionService.revokeAllOfTenant(tenantId, SessionService.REASON_TENANT_SUSPENDED));
        }
        recordPlatformEvent("TENANT_STATUS_CHANGED", tenantId, after.code(), Map.of("status", before.status()),
                Map.of("status", after.status()), request.reason());
        return after;
    }

    public TenantDetailsResponse setFeatureLicensed(UUID tenantId, String featureCode, boolean licensed) {
        TenantSummary tenant = provisioningService.get(tenantId);
        boolean previous = TenantContext.callAs(tenantId,
                () -> provisioningService.setFeatureLicensed(tenantId, featureCode, licensed));
        if (previous != licensed) {
            recordPlatformEvent(licensed ? "FEATURE_LICENSED" : "FEATURE_UNLICENSED", tenantId, tenant.code(),
                    Map.of("feature", featureCode, "licensed", previous),
                    Map.of("feature", featureCode, "licensed", licensed), null);
        }
        return get(tenantId);
    }

    public List<FeatureResponse> featureCatalog() {
        return provisioningService.catalog();
    }

    private void recordPlatformEvent(String action, UUID tenantId, String tenantCode, Object before, Object after,
                                     String reason) {
        transactionTemplate.executeWithoutResult(status -> auditService.record(AuditEvent.builder(action, RESOURCE)
                .resourceId(tenantId)
                .resourceReference(tenantCode)
                .before(before)
                .after(after)
                .metadata("reason", reason)
                .build()));
    }
}
