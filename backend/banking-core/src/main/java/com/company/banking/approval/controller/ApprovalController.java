package com.company.banking.approval.controller;

import com.company.banking.approval.dto.ApprovalPolicyRequest;
import com.company.banking.approval.dto.ApprovalPolicyResponse;
import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.approval.dto.DecisionRequest;
import com.company.banking.approval.model.ApprovalType;
import com.company.banking.approval.service.ApprovalService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Approvals")
public class ApprovalController {

    private final ApprovalService approvalService;

    @GetMapping("/approvals")
    @PreAuthorize("hasAuthority('approval.view')")
    @Operation(summary = "Approval requests in the caller's branch scope, oldest first")
    public ApiResponse<PageResponse<ApprovalResponse>> search(
            @RequestParam(required = false) @Pattern(regexp = "^(PENDING|APPROVED|REJECTED|CANCELLED)$") String status,
            @RequestParam(required = false)
            @Pattern(regexp = "^(TRANSACTION_REVERSAL|MANUAL_JOURNAL|CASH_WITHDRAWAL|TRANSFER)$") String type,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(approvalService.search(status, type, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/approvals/{id}")
    @PreAuthorize("hasAnyAuthority('approval.view', 'transaction.create', 'transaction.reverse', 'ledger.post')")
    @Operation(summary = "An approval request (makers see their own)")
    public ApiResponse<ApprovalResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(approvalService.get(id));
    }

    @PostMapping("/approvals/{id}/approve")
    @PreAuthorize("hasAuthority('approval.act')")
    @Operation(summary = "Approve a request and run its action (not your own request)")
    public ApiResponse<ApprovalResponse> approve(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Approved", approvalService.approve(id, request));
    }

    @PostMapping("/approvals/{id}/reject")
    @PreAuthorize("hasAuthority('approval.act')")
    @Operation(summary = "Reject a request with a reason")
    public ApiResponse<ApprovalResponse> reject(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Rejected", approvalService.reject(id, request));
    }

    @PostMapping("/approvals/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('transaction.create', 'transaction.reverse', 'ledger.post')")
    @Operation(summary = "Withdraw your own pending request")
    public ApiResponse<ApprovalResponse> cancel(@PathVariable UUID id, @Valid @RequestBody DecisionRequest request) {
        return ApiResponse.ok("Cancelled", approvalService.cancel(id, request));
    }

    @GetMapping("/approval-policies")
    @PreAuthorize("hasAnyAuthority('settings.view', 'settings.manage', 'approval.view')")
    @Operation(summary = "Thresholds above which withdrawals and transfers need a checker")
    public ApiResponse<List<ApprovalPolicyResponse>> policies() {
        return ApiResponse.ok(approvalService.policies());
    }

    @PutMapping("/approval-policies/{type}/{currency}")
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Set or change a threshold")
    public ApiResponse<ApprovalPolicyResponse> configure(
            @PathVariable @Pattern(regexp = "^(CASH_WITHDRAWAL|TRANSFER)$") String type,
            @PathVariable @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @Valid @RequestBody ApprovalPolicyRequest request) {
        return ApiResponse.ok("Threshold saved", approvalService.configure(ApprovalType.valueOf(type), currency,
                request));
    }
}
