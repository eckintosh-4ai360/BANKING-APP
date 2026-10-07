package com.company.banking.staff.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.iam.dto.IssuedCredential;
import com.company.banking.staff.dto.ChangeStaffStatusRequest;
import com.company.banking.staff.dto.CreateStaffRequest;
import com.company.banking.staff.dto.StaffCreatedResponse;
import com.company.banking.staff.dto.StaffResponse;
import com.company.banking.staff.dto.StaffSummary;
import com.company.banking.staff.dto.UpdateStaffRequest;
import com.company.banking.staff.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff")
@RequiredArgsConstructor
@Tag(name = "Staff")
public class StaffController {

    private final StaffService staffService;

    @GetMapping
    @PreAuthorize("hasAuthority('staff.view')")
    @Operation(summary = "List staff within the caller's branch scope")
    public ApiResponse<PageResponse<StaffSummary>> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|SUSPENDED|TERMINATED)$") String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(staffService.list(q, branchId, status, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('staff.view')")
    @Operation(summary = "Get a staff member with roles and login state")
    public ApiResponse<StaffResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(staffService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('staff.create')")
    @Operation(summary = "Create a staff member",
            description = "Returns a one-time temporary password that must be changed at first login. "
                    + "Assigning roles at creation additionally requires role.assign.")
    public ApiResponse<StaffCreatedResponse> create(@Valid @RequestBody CreateStaffRequest request) {
        return ApiResponse.ok("Staff created", staffService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('staff.edit')")
    @Operation(summary = "Update a staff member's profile and branch access (not yourself)")
    public ApiResponse<StaffResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateStaffRequest request) {
        return ApiResponse.ok("Staff updated", staffService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('staff.disable')")
    @Operation(summary = "Suspend, reactivate or terminate a staff member (not yourself)")
    public ApiResponse<StaffResponse> changeStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody ChangeStaffStatusRequest request) {
        return ApiResponse.ok("Staff status changed", staffService.changeStatus(id, request));
    }

    @PostMapping("/{id}/credentials/reset")
    @PreAuthorize("hasAuthority('staff.unlock')")
    @Operation(summary = "Unlock and issue a new temporary password (not yourself)")
    public ApiResponse<IssuedCredential> resetCredential(@PathVariable UUID id) {
        return ApiResponse.ok("Credentials reset", staffService.resetCredential(id));
    }
}
