package com.company.banking.fieldops.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.entity.FieldAlert;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.FieldAlertRepository;
import com.company.banking.fieldops.repository.SequenceGapRepository;
import com.company.banking.fieldops.repository.SequenceGapRepository.Gap;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Field alerts: sequence gaps, conflicting resubmissions, late syncs and offline cash above the limit. A gap alert
 * resolves itself when the missing collections arrive; a supervisor resolves the rest with a note.
 */
@Service
@RequiredArgsConstructor
public class FieldAlertService {

    static final String RESOURCE = "FIELD_ALERT";

    private final FieldAlertRepository alerts;
    private final SequenceGapRepository gaps;
    private final FieldOfficerService officerService;
    private final FieldScope fieldScope;
    private final AuditService auditService;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PageResponse<FieldResponses.Alert> search(UUID officerId, String status, PageRequest page) {
        Set<UUID> officers = fieldScope.officers(officerId);
        FieldAlert.Status wanted = status == null ? null : FieldAlert.Status.valueOf(status);
        if (officers.isEmpty()) {
            return PageResponse.of(List.of(), page.getPageNumber(), page.getPageSize(), 0);
        }
        return PageResponse.from(alerts.search(TenantContext.requireTenantId(), officers, wanted, page),
                FieldAlertService::toResponse);
    }

    @Transactional
    public FieldResponses.Alert resolve(UUID alertId, OfficerRequests.Decision request) {
        FieldAlert alert = alerts.findByTenantIdAndId(TenantContext.requireTenantId(), alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Field alert"));
        FieldOfficer officer = officerService.loadInScope(alert.getOfficerId());
        if (!alert.getVersion().equals(request.version())) {
            throw new BankingException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        if (alert.getStatus() != FieldAlert.Status.OPEN) {
            throw new BankingException(FieldErrorCode.ALERT_NOT_OPEN);
        }
        alert.resolve(clock.instant(), CurrentActor.currentActorId().orElse(null), request.note().trim());
        alerts.saveAndFlush(alert);
        auditService.record(AuditEvent.builder("FIELD_ALERT_RESOLVED", RESOURCE)
                .resourceId(alertId)
                .resourceReference(alert.getAlertType().name())
                .branchId(officer.getBranchId())
                .metadata("note", request.note().trim())
                .build());
        return toResponse(alert);
    }

    // ----------------------------------------------------------------------------------- for the module

    @Transactional(propagation = Propagation.MANDATORY)
    void raise(FieldOfficer officer, UUID deviceId, FieldAlert.Type type, String detail, UUID clientReference) {
        save(officer, deviceId, type, detail, null, null, clientReference);
    }

    /**
     * Brings the device's gap alerts up to date: a gap without an open alert gets one, and an open gap alert that
     * no longer matches a gap exactly is resolved (the missing collections arrived, or some of them did and the
     * rest gets a new alert).
     *
     * @return the device's gaps now
     */
    @Transactional(propagation = Propagation.MANDATORY)
    List<Gap> refreshGaps(FieldOfficer officer, UUID deviceId) {
        UUID tenantId = officer.getTenantId();
        List<Gap> current = gaps.gaps(tenantId, deviceId);
        List<FieldAlert> open = alerts.findAllByTenantIdAndDeviceIdAndAlertTypeAndStatus(tenantId, deviceId,
                FieldAlert.Type.SEQUENCE_GAP, FieldAlert.Status.OPEN);
        Instant now = clock.instant();
        for (FieldAlert alert : open) {
            if (!current.contains(new Gap(alert.getMissingFrom(), alert.getMissingTo()))) {
                alert.resolve(now, null, "The missing collections arrived");
                alerts.save(alert);
            }
        }
        for (Gap gap : current) {
            boolean known = open.stream().anyMatch(alert -> alert.getMissingFrom() == gap.from()
                    && alert.getMissingTo() == gap.to());
            if (!known) {
                String numbers = gap.from() == gap.to() ? "Collection " + gap.from()
                        : "Collections " + gap.from() + " to " + gap.to();
                save(officer, deviceId, FieldAlert.Type.SEQUENCE_GAP, numbers
                        + " of the device never arrived", gap.from(), gap.to(), null);
            }
        }
        alerts.flush();
        return current;
    }

    private void save(FieldOfficer officer, UUID deviceId, FieldAlert.Type type, String detail, Long missingFrom,
                      Long missingTo, UUID clientReference) {
        FieldAlert alert = alerts.save(new FieldAlert(UuidV7.next(), officer.getTenantId(), officer.getStaffId(),
                deviceId, type, detail, missingFrom, missingTo, clientReference, clock.instant()));
        auditService.record(AuditEvent.builder("FIELD_ALERT_RAISED", RESOURCE)
                .resourceId(alert.getId())
                .resourceReference(type.name())
                .branchId(officer.getBranchId())
                .metadata("officerId", officer.getStaffId().toString())
                .metadata("detail", detail)
                .build());
    }

    static FieldResponses.Alert toResponse(FieldAlert alert) {
        return new FieldResponses.Alert(alert.getId(), alert.getOfficerId(), alert.getDeviceId(),
                alert.getAlertType().name(), alert.getDetail(), alert.getMissingFrom(), alert.getMissingTo(),
                alert.getClientReference(), alert.getStatus().name(), alert.getRaisedAt(), alert.getResolvedAt(),
                alert.getResolvedBy(), alert.getResolution(), alert.getVersion());
    }
}
