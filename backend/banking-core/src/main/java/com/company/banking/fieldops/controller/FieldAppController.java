package com.company.banking.fieldops.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.fieldops.dto.FieldResponses;
import com.company.banking.fieldops.dto.OfficerRequests;
import com.company.banking.fieldops.dto.RemittanceDtos;
import com.company.banking.fieldops.dto.SyncRequest;
import com.company.banking.fieldops.dto.SyncResponse;
import com.company.banking.fieldops.service.CollectionSyncService;
import com.company.banking.fieldops.service.CollectorRemittanceService;
import com.company.banking.fieldops.service.CustomerAssignmentService;
import com.company.banking.fieldops.service.FieldDeviceService;
import com.company.banking.fieldops.service.FieldOfficerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the field app uses (the signed-in officer's own data and sync), and the teller receiving an officer's cash.
 */
@RestController
@RequestMapping("/api/v1/field")
@RequiredArgsConstructor
@Tag(name = "Field app")
public class FieldAppController {

    private final FieldOfficerService officerService;
    private final CustomerAssignmentService assignmentService;
    private final FieldDeviceService deviceService;
    private final CollectionSyncService syncService;
    private final CollectorRemittanceService remittanceService;

    @GetMapping("/me")
    @PreAuthorize("hasAuthority('collection.create')")
    @Operation(summary = "The signed-in field officer: profile, cash carried and devices")
    public ApiResponse<FieldResponses.Me> me() {
        return ApiResponse.ok(officerService.me());
    }

    @GetMapping("/me/customers")
    @PreAuthorize("hasAuthority('collection.create')")
    @Operation(summary = "The officer's customers and the accounts a collection can go to (kept on the phone)")
    public ApiResponse<List<FieldResponses.MyCustomer>> myCustomers() {
        return ApiResponse.ok(assignmentService.myCustomers());
    }

    @PostMapping("/devices")
    @PreAuthorize("hasAuthority('collection.create')")
    @Operation(summary = "Register the officer's phone (again returns the same registration)")
    public ApiResponse<FieldResponses.Device> registerDevice(
            @Valid @RequestBody OfficerRequests.RegisterDevice request) {
        return ApiResponse.ok("Device registered", deviceService.register(request));
    }

    @PostMapping("/sync")
    @PreAuthorize("hasAuthority('collection.create')")
    @Operation(summary = "Send collections and visits recorded offline; sending them again changes nothing")
    public ApiResponse<SyncResponse> sync(@Valid @RequestBody SyncRequest request) {
        return ApiResponse.ok("Synced", syncService.sync(request));
    }

    @PostMapping("/remittances")
    @PreAuthorize("hasAuthority('teller.operate')")
    @Operation(summary = "Receive a field officer's cash into the teller's drawer")
    public ResponseEntity<ApiResponse<RemittanceDtos.Remittance>> remit(
            @Parameter(description = "Unique per intended remittance; reuse it only to retry")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody RemittanceDtos.Remit request) {
        Result<RemittanceDtos.Remittance> result = remittanceService.remit(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(ApiResponse.ok("Cash received", result.response()));
    }
}
