package com.company.banking.fieldops.service;

import com.company.banking.audit.service.AuditEvent;
import com.company.banking.audit.service.AuditService;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.error.ResourceNotFoundException;
import com.company.banking.common.id.UuidV7;
import com.company.banking.common.security.BranchScope;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.entity.FieldDevice;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.exception.FieldErrorCode;
import com.company.banking.fieldops.repository.FieldDeviceRepository;
import com.company.banking.fieldops.repository.FieldOfficerRepository;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Phones registered to field officers. Only a live registration of the officer's own may sync; a supervisor
 * revokes a lost or replaced phone.
 */
@Service
@RequiredArgsConstructor
public class FieldDeviceService {

    static final String RESOURCE = "FIELD_DEVICE";

    private final FieldDeviceRepository devices;
    private final FieldOfficerRepository officers;
    private final FieldOfficerService officerService;
    private final AuditService auditService;
    private final Clock clock;

    /**
     * Registers the signed-in officer's phone. Registering the same phone again returns its registration; a phone
     * registered to someone else must be revoked first.
     */
    @Transactional
    public FieldResponses.Device register(OfficerRequests.RegisterDevice request) {
        FieldOfficer officer = officerService.requireCurrentOfficer();
        Optional<FieldDevice> live = devices.findLiveByKey(officer.getTenantId(), request.deviceKey());
        if (live.isPresent()) {
            if (!live.get().getOfficerId().equals(officer.getStaffId())) {
                throw new BankingException(FieldErrorCode.DEVICE_IN_USE);
            }
            return toResponse(live.get());
        }
        FieldDevice device = devices.saveAndFlush(new FieldDevice(UuidV7.next(), officer.getTenantId(),
                officer.getStaffId(), request.deviceKey(), request.name().trim(), clock.instant()));
        FieldResponses.Device response = toResponse(device);
        auditService.record(AuditEvent.builder("FIELD_DEVICE_REGISTERED", RESOURCE)
                .resourceId(device.getId())
                .resourceReference(device.getName())
                .branchId(officer.getBranchId())
                .after(response)
                .build());
        return response;
    }

    /**
     * Devices of the officers in the caller's branches (of one officer when given).
     */
    @Transactional(readOnly = true)
    public List<FieldResponses.Device> list(UUID officerId) {
        UUID tenantId = TenantContext.requireTenantId();
        if (officerId != null) {
            officerService.loadInScope(officerId);
            return devices.findAllByTenantIdAndOfficerIdOrderByRegisteredAtDesc(tenantId, officerId).stream()
                    .map(FieldDeviceService::toResponse).toList();
        }
        BranchScope scope = CurrentActor.require().branchScope();
        Map<UUID, UUID> branches = officerBranches(tenantId);
        return devices.findAllByTenantIdOrderByRegisteredAtDesc(tenantId).stream()
                .filter(device -> scope.permits(branches.get(device.getOfficerId())))
                .map(FieldDeviceService::toResponse)
                .toList();
    }

    @Transactional
    public FieldResponses.Device revoke(UUID deviceId, OfficerRequests.Decision request) {
        UUID tenantId = TenantContext.requireTenantId();
        FieldDevice device = devices.lockByTenantIdAndId(tenantId, deviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Field device"));
        FieldOfficer officer = officerService.loadInScope(device.getOfficerId());
        if (!device.getVersion().equals(request.version())) {
            throw new BankingException(CommonErrorCode.CONCURRENT_MODIFICATION);
        }
        if (!device.isLive()) {
            throw new BankingException(FieldErrorCode.DEVICE_REVOKED);
        }
        device.revoke(clock.instant(), CurrentActor.currentActorId().orElse(null), request.note().trim());
        devices.saveAndFlush(device);
        auditService.record(AuditEvent.builder("FIELD_DEVICE_REVOKED", RESOURCE)
                .resourceId(deviceId)
                .resourceReference(device.getName())
                .branchId(officer.getBranchId())
                .metadata("reason", request.note().trim())
                .build());
        return toResponse(device);
    }

    // ----------------------------------------------------------------------------------- for the module

    /**
     * The device, locked for the rest of the transaction, if it is a live registration of the officer.
     */
    FieldDevice lockOwnLiveDevice(UUID deviceId, UUID officerId) {
        return devices.lockByTenantIdAndId(TenantContext.requireTenantId(), deviceId)
                .filter(device -> device.getOfficerId().equals(officerId) && device.isLive())
                .orElseThrow(() -> new BankingException(FieldErrorCode.DEVICE_NOT_REGISTERED));
    }

    static FieldResponses.Device toResponse(FieldDevice device) {
        return new FieldResponses.Device(device.getId(), device.getOfficerId(), device.getName(),
                device.getLastSequenceNo(), device.getRegisteredAt(), device.getLastSyncedAt(), device.isLive(),
                device.getRevokedAt(), device.getRevokeReason(), device.getVersion());
    }

    private Map<UUID, UUID> officerBranches(UUID tenantId) {
        return officers.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream()
                .collect(Collectors.toMap(FieldOfficer::getStaffId, FieldOfficer::getBranchId));
    }
}
