package com.company.banking.branch.controller;

import com.company.banking.branch.dto.BranchResponse;
import com.company.banking.branch.dto.ChangeBranchStatusRequest;
import com.company.banking.branch.dto.CreateBranchRequest;
import com.company.banking.branch.dto.UpdateBranchRequest;
import com.company.banking.branch.service.BranchService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
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
@RequestMapping("/api/v1/branches")
@RequiredArgsConstructor
@Tag(name = "Branches")
public class BranchController {

    private final BranchService branchService;

    @GetMapping
    @PreAuthorize("hasAuthority('branch.view')")
    @Operation(summary = "List branches within the caller's branch scope")
    public ApiResponse<PageResponse<BranchResponse>> list(
            @RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|INACTIVE|CLOSED)$") String status,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(branchService.list(status, q, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('branch.view')")
    @Operation(summary = "Get a branch")
    public ApiResponse<BranchResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(branchService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('branch.manage')")
    @Operation(summary = "Create a branch (requires all-branch access)")
    public ApiResponse<BranchResponse> create(@Valid @RequestBody CreateBranchRequest request) {
        return ApiResponse.ok("Branch created", branchService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('branch.manage')")
    @Operation(summary = "Update branch details")
    public ApiResponse<BranchResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateBranchRequest request) {
        return ApiResponse.ok("Branch updated", branchService.update(id, request));
    }

    @PostMapping("/{id}/status")
    @PreAuthorize("hasAuthority('branch.manage')")
    @Operation(summary = "Activate, deactivate or close a branch")
    public ApiResponse<BranchResponse> changeStatus(@PathVariable UUID id,
                                                    @Valid @RequestBody ChangeBranchStatusRequest request) {
        return ApiResponse.ok("Branch status changed", branchService.changeStatus(id, request));
    }
}
