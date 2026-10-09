package com.company.banking.fieldops.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.dto.RemittanceDtos;
import com.company.banking.fieldops.service.CollectionSyncService;
import com.company.banking.fieldops.service.CollectorRemittanceService;
import com.company.banking.fieldops.service.CustomerAssignmentService;
import com.company.banking.fieldops.service.FieldAlertService;
import com.company.banking.fieldops.service.FieldDeviceService;
import com.company.banking.fieldops.service.FieldOfficerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Supervising field work: officers and their limits, customer assignments, devices, alerts, and what was collected,
 * visited and remitted.
 */
@Validated
@RestController
@RequestMapping("/api/v1/field")
@RequiredArgsConstructor
@Tag(name = "Field operations")
public class FieldOperationsController {

    private static final String SUPERVISE = "hasAuthority('field.manage')";
    private static final String READ = "hasAnyAuthority('field.manage', 'collection.view')";
    private static final String READ_OWN = "hasAnyAuthority('field.manage', 'collection.view', 'collection.create')";

    private final FieldOfficerService officerService;
    private final CustomerAssignmentService assignmentService;
    private final FieldDeviceService deviceService;
    private final FieldAlertService alertService;
    private final CollectionSyncService collectionService;
    private final CollectorRemittanceService remittanceService;

    @GetMapping("/officers")
    @PreAuthorize("hasAnyAuthority('field.manage', 'collection.view', 'teller.operate')")
    @Operation(summary = "Field officers of the caller's branches (tellers pick one to receive their cash)")
    public ApiResponse<List<FieldResponses.Officer>> officers() {
        return ApiResponse.ok(officerService.list());
    }

    @PostMapping("/officers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Make a staff member a field officer (opens their cash with collectors account)")
    public ApiResponse<FieldResponses.Officer> register(@Valid @RequestBody OfficerRequests.Register request) {
        return ApiResponse.ok("Field officer registered", officerService.register(request));
    }

    @GetMapping("/officers/{id}")
    @PreAuthorize(READ)
    @Operation(summary = "A field officer")
    public ApiResponse<FieldResponses.Officer> officer(@PathVariable UUID id) {
        return ApiResponse.ok(officerService.get(id));
    }

    @PutMapping("/officers/{id}")
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Change a field officer's target and offline limits")
    public ApiResponse<FieldResponses.Officer> update(@PathVariable UUID id,
                                                      @Valid @RequestBody OfficerRequests.Update request) {
        return ApiResponse.ok("Field officer updated", officerService.update(id, request));
    }

    @PostMapping("/officers/{id}/status")
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Suspend or reinstate a field officer")
    public ApiResponse<FieldResponses.Officer> changeStatus(@PathVariable UUID id,
                                                            @Valid @RequestBody OfficerRequests.ChangeStatus request) {
        return ApiResponse.ok("Status changed", officerService.changeStatus(id, request));
    }

    @GetMapping("/officers/{id}/cash-position")
    @PreAuthorize("hasAnyAuthority('field.manage', 'collection.view', 'teller.operate', 'cash.view')")
    @Operation(summary = "The cash a field officer carries, reconciled with their collections and remittances")
    public ApiResponse<FieldResponses.CashPosition> cashPosition(@PathVariable UUID id) {
        return ApiResponse.ok(officerService.cashPosition(id));
    }

    @GetMapping("/assignments")
    @PreAuthorize(READ)
    @Operation(summary = "Customer assignments (current ones by default)")
    public ApiResponse<PageResponse<FieldResponses.Assignment>> assignments(
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "true") boolean activeOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(assignmentService.search(officerId, customerId, activeOnly,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Assign a customer to a field officer (ends any previous assignment)")
    public ApiResponse<FieldResponses.Assignment> assign(@Valid @RequestBody OfficerRequests.Assign request) {
        return ApiResponse.ok("Customer assigned", assignmentService.assign(request));
    }

    @PostMapping("/assignments/{id}/end")
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "End a customer assignment")
    public ApiResponse<FieldResponses.Assignment> endAssignment(
            @PathVariable UUID id, @Valid @RequestBody OfficerRequests.EndAssignment request) {
        return ApiResponse.ok("Assignment ended", assignmentService.end(id, request));
    }

    @GetMapping("/devices")
    @PreAuthorize(READ)
    @Operation(summary = "Phones registered to the caller's field officers")
    public ApiResponse<List<FieldResponses.Device>> devices(@RequestParam(required = false) UUID officerId) {
        return ApiResponse.ok(deviceService.list(officerId));
    }

    @PostMapping("/devices/{id}/revoke")
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Revoke a lost or replaced phone; it can no longer sync")
    public ApiResponse<FieldResponses.Device> revoke(@PathVariable UUID id,
                                                     @Valid @RequestBody OfficerRequests.Decision request) {
        return ApiResponse.ok("Device revoked", deviceService.revoke(id, request));
    }

    @GetMapping("/alerts")
    @PreAuthorize(READ)
    @Operation(summary = "Field alerts: sequence gaps, conflicts, late syncs, offline cash above the limit")
    public ApiResponse<PageResponse<FieldResponses.Alert>> alerts(
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) @Pattern(regexp = "^(OPEN|RESOLVED)$") String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(alertService.search(officerId, status, PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/alerts/{id}/resolve")
    @PreAuthorize(SUPERVISE)
    @Operation(summary = "Resolve a field alert with a note")
    public ApiResponse<FieldResponses.Alert> resolve(@PathVariable UUID id,
                                                     @Valid @RequestBody OfficerRequests.Decision request) {
        return ApiResponse.ok("Alert resolved", alertService.resolve(id, request));
    }

    @GetMapping("/collections")
    @PreAuthorize(READ_OWN)
    @Operation(summary = "Collections received from the field (an officer sees their own), newest first")
    public ApiResponse<PageResponse<FieldResponses.CollectionView>> collections(
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @Pattern(regexp = "^(POSTED|REJECTED)$") String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(collectionService.search(officerId, customerId, status, from, to,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/visits")
    @PreAuthorize(READ_OWN)
    @Operation(summary = "Customer visits (an officer sees their own), newest first")
    public ApiResponse<PageResponse<FieldResponses.Visit>> visits(
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(collectionService.visits(officerId, customerId,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/remittances")
    @PreAuthorize(READ_OWN)
    @Operation(summary = "Cash field officers handed to tellers, newest first")
    public ApiResponse<PageResponse<RemittanceDtos.Remittance>> remittances(
            @RequestParam(required = false) UUID officerId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(remittanceService.search(officerId, PageRequests.of(page, size, Sort.unsorted())));
    }
}
