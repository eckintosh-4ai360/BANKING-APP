package com.company.banking.fieldops.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.CustomerAssignmentRepository;
import com.company.banking.fieldops.repository.FieldAlertRepository;
import com.company.banking.fieldops.repository.FieldCashRepository;
import com.company.banking.fieldops.repository.FieldDeviceRepository;
import com.company.banking.fieldops.repository.FieldOfficerRepository;
import com.company.banking.ledger.dto.OpenLedgerAccountCommand;
import com.company.banking.ledger.model.LedgerAccountType;
import com.company.banking.ledger.model.SystemAccount;
import com.company.banking.ledger.service.BusinessDateService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerAccountService;
import com.company.banking.staff.dto.StaffResponse;
import com.company.banking.staff.service.StaffService;
import com.company.banking.tenant.service.TenantService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field officers: staff who collect in the field, each with a cash with collectors account for the cash they carry.
 */
@Service
@RequiredArgsConstructor
public class FieldOfficerService {

    static final String RESOURCE = "FIELD_OFFICER";

    private final FieldOfficerRepository officers;
    private final CustomerAssignmentRepository assignments;
    private final FieldDeviceRepository devices;
    private final FieldAlertRepository alerts;
    private final FieldCashRepository cash;
    private final StaffService staffService;
    private final LedgerAccountService ledgerAccounts;
    private final CurrencyService currencies;
    private final BusinessDateService businessDates;
    private final TenantService tenantService;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional
    public FieldResponses.Officer register(OfficerRequests.Register request) {
        UUID tenantId = TenantContext.requireTenantId();
        StaffResponse staff = staffService.get(request.staffId());
        if (!"ACTIVE".equals(staff.status())) {
            throw new BankingException(FieldErrorCode.STAFF_NOT_ACTIVE);
        }
        if (officers.existsByTenantIdAndStaffId(tenantId, staff.id())) {
            throw new BankingException(FieldErrorCode.OFFICER_EXISTS);
        }
        String currency = currencies.require(request.currency() != null ? request.currency()
                : tenantService.getCurrent().baseCurrency()).code();
        BigDecimal maxOffline = validAmount(request.maxOfflineAmount(), currency);
        UUID ledgerAccount = ledgerAccounts.open(new OpenLedgerAccountCommand(null, SystemAccount.COLLECTOR_CASH,
                staff.homeBranchId(), currency, LedgerAccountType.COLLECTOR_CASH, true,
                "Collector " + staff.firstName() + " " + staff.lastName(), RESOURCE, staff.id())).id();
        FieldOfficer officer = officers.saveAndFlush(new FieldOfficer(staff.id(), tenantId, staff.homeBranchId(),
                currency, ledgerAccount, validAmountOrNull(request.dailyTarget(), currency), maxOffline,
                request.maxOfflineHours(), clock.instant(), CurrentActor.currentActorId().orElse(null)));
        FieldResponses.Officer response = toResponse(officer, staff.firstName(), staff.lastName());
        auditService.record(AuditEvent.builder("FIELD_OFFICER_REGISTERED", RESOURCE)
                .resourceId(officer.getStaffId())
                .branchId(officer.getBranchId())
                .after(response)
                .build());
        return response;
    }

    @Transactional
    public FieldResponses.Officer update(UUID officerId, OfficerRequests.Update request) {
        FieldOfficer officer = loadInScope(officerId);
        requireVersion(officer.getVersion(), request.version());
        FieldResponses.Officer before = toResponse(officer);
        officer.changeLimits(validAmountOrNull(request.dailyTarget(), officer.getCurrency()),
                validAmount(request.maxOfflineAmount(), officer.getCurrency()), request.maxOfflineHours(),
                clock.instant());
        officers.saveAndFlush(officer);
        FieldResponses.Officer after = toResponse(officer);
        auditService.record(AuditEvent.builder("FIELD_OFFICER_UPDATED", RESOURCE)
                .resourceId(officerId)
                .branchId(officer.getBranchId())
                .before(before)
                .after(after)
                .build());
        return after;
    }

    /**
     * Suspends an officer (collections from their devices are then rejected and recorded) or reinstates them.
     */
    @Transactional
    public FieldResponses.Officer changeStatus(UUID officerId, OfficerRequests.ChangeStatus request) {
        FieldOfficer officer = loadInScope(officerId);
        requireVersion(officer.getVersion(), request.version());
        FieldOfficer.Status before = officer.getStatus();
        officer.changeStatus(FieldOfficer.Status.valueOf(request.status()), clock.instant());
        officers.saveAndFlush(officer);
        auditService.record(AuditEvent.builder("FIELD_OFFICER_STATUS_CHANGED", RESOURCE)
                .resourceId(officerId)
                .branchId(officer.getBranchId())
                .before(Map.of("status", before))
                .after(Map.of("status", officer.getStatus()))
                .metadata("reason", request.reason())
                .build());
        return toResponse(officer);
    }

    @Transactional(readOnly = true)
    public List<FieldResponses.Officer> list() {
        BranchScope scope = CurrentActor.require().branchScope();
        List<FieldOfficer> visible = officers.findAllByTenantIdOrderByCreatedAtAsc(TenantContext.requireTenantId())
                .stream()
                .filter(officer -> scope.permits(officer.getBranchId()))
                .toList();
        Map<UUID, String> names = staffService.names(visible.stream().map(FieldOfficer::getStaffId).toList());
        return visible.stream().map(officer -> toResponse(officer, names.get(officer.getStaffId()))).toList();
    }

    @Transactional(readOnly = true)
    public FieldResponses.Officer get(UUID officerId) {
        return toResponse(loadInScope(officerId));
    }

    @Transactional(readOnly = true)
    public FieldResponses.CashPosition cashPosition(UUID officerId) {
        return position(loadInScope(officerId));
    }

    /**
     * The signed-in officer's profile, cash and devices (for the field app).
     */
    @Transactional(readOnly = true)
    public FieldResponses.Me me() {
        FieldOfficer officer = requireCurrentOfficer();
        return new FieldResponses.Me(toResponse(officer), position(officer),
                devices.findAllByTenantIdAndOfficerIdOrderByRegisteredAtDesc(officer.getTenantId(),
                        officer.getStaffId()).stream().map(FieldDeviceService::toResponse).toList());
    }

    // ----------------------------------------------------------------------------------- for the module

    /**
     * The signed-in staff member as a field officer (403 when they are not one).
     */
    FieldOfficer requireCurrentOfficer() {
        UUID staffId = CurrentActor.require().id();
        return officers.findByTenantIdAndStaffId(TenantContext.requireTenantId(), staffId)
                .orElseThrow(() -> new BankingException(FieldErrorCode.NOT_A_FIELD_OFFICER));
    }

    FieldOfficer loadInScope(UUID officerId) {
        BranchScope scope = CurrentActor.require().branchScope();
        return officers.findByTenantIdAndStaffId(TenantContext.requireTenantId(), officerId)
                .filter(officer -> scope.permits(officer.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("Field officer"));
    }

    FieldResponses.CashPosition position(FieldOfficer officer) {
        UUID tenantId = officer.getTenantId();
        LocalDate today = businessDates.today();
        String currency = officer.getCurrency();
        BigDecimal ledger = ledgerAccounts.balance(officer.getCollectorLedgerAccountId()).ledgerBalance();
        BigDecimal collected = cash.collected(tenantId, officer.getStaffId(), null);
        BigDecimal remitted = cash.remitted(tenantId, officer.getStaffId(), null);
        BigDecimal expected = collected.subtract(remitted);
        BigDecimal difference = ledger.subtract(expected);
        return new FieldResponses.CashPosition(officer.getStaffId(), currency, today,
                currencies.present(ledger, currency), currencies.present(collected, currency),
                currencies.present(remitted, currency), currencies.present(expected, currency),
                currencies.present(difference, currency), difference.signum() == 0,
                currencies.present(cash.collected(tenantId, officer.getStaffId(), today), currency),
                currencies.present(cash.remitted(tenantId, officer.getStaffId(), today), currency));
    }

    FieldResponses.Officer toResponse(FieldOfficer officer) {
        return toResponse(officer, staffService.names(List.of(officer.getStaffId())).get(officer.getStaffId()));
    }

    private FieldResponses.Officer toResponse(FieldOfficer officer, String fullName) {
        String name = Objects.requireNonNullElse(fullName, "");
        int space = name.indexOf(' ');
        return toResponse(officer, space < 0 ? name : name.substring(0, space),
                space < 0 ? "" : name.substring(space + 1));
    }

    private FieldResponses.Officer toResponse(FieldOfficer officer, String firstName, String lastName) {
        UUID tenantId = officer.getTenantId();
        String currency = officer.getCurrency();
        return new FieldResponses.Officer(officer.getStaffId(), firstName, lastName, officer.getBranchId(), currency,
                officer.getStatus().name(), presentOrNull(officer.getDailyTarget(), currency),
                currencies.present(officer.getMaxOfflineAmount(), currency), officer.getMaxOfflineHours(),
                currencies.present(ledgerAccounts.balance(officer.getCollectorLedgerAccountId()).ledgerBalance(),
                        currency),
                assignments.countActiveByOfficer(tenantId, officer.getStaffId()),
                alerts.countOpenByOfficer(tenantId, officer.getStaffId()), officer.getCreatedAt(),
                officer.getVersion());
    }

    /** An amount from a request: above zero, in whole minor units of the currency. */
    private BigDecimal validAmount(BigDecimal amount, String currency) {
        currencies.requireValidAmount(amount, currency);
        return currencies.present(amount, currency);
    }

    private BigDecimal validAmountOrNull(BigDecimal amount, String currency) {
        return amount == null ? null : validAmount(amount, currency);
    }

    private BigDecimal presentOrNull(BigDecimal amount, String currency) {
        return amount == null ? null : currencies.present(amount, currency);
    }

    private static void requireVersion(Long current, Long expected) {
        if (!current.equals(expected)) {
            throw new BankingException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
    }
}
