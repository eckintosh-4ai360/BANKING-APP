package com.company.banking.kyc.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.kyc.dto.KycCaseResponse;
import com.company.banking.kyc.dto.KycCaseSummary;
import com.company.banking.kyc.dto.KycDecisionRequest;
import com.company.banking.kyc.dto.KycNoteRequest;
import com.company.banking.kyc.dto.ManualCheckRequest;
import com.company.banking.kyc.dto.OpenKycCaseRequest;
import com.company.banking.kyc.service.KycCaseService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "KYC cases", description = "Capture, review and four-eyes decisions on customer verification")
public class KycCaseController {

    private static final String CAPTURE = "hasAnyAuthority('customer.edit', 'customer.create')";

    private final KycCaseService kycCaseService;

    @PostMapping("/customers/{customerId}/kyc-cases")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Open a KYC case for a customer")
    public ApiResponse<KycCaseResponse> open(@PathVariable UUID customerId,
                                             @Valid @RequestBody OpenKycCaseRequest request) {
        return ApiResponse.ok("KYC case opened", kycCaseService.open(customerId, request));
    }

    @GetMapping("/customers/{customerId}/kyc-cases")
    @PreAuthorize("hasAuthority('kyc.view')")
    @Operation(summary = "KYC history of a customer")
    public ApiResponse<List<KycCaseSummary>> casesOfCustomer(@PathVariable UUID customerId) {
        return ApiResponse.ok(kycCaseService.casesOfCustomer(customerId));
    }

    @GetMapping("/kyc/cases")
    @PreAuthorize("hasAuthority('kyc.view')")
    @Operation(summary = "KYC case queue in the caller's branch scope (oldest first)")
    public ApiResponse<PageResponse<KycCaseSummary>> search(
            @RequestParam(required = false)
            @Pattern(regexp = "^(OPEN|PENDING_REVIEW|RETURNED|APPROVED|REJECTED|CANCELLED)$") String status,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(kycCaseService.search(status, branchId, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/kyc/cases/{caseId}")
    @PreAuthorize("hasAuthority('kyc.view')")
    @Operation(summary = "A KYC case with requirement status and recorded checks")
    public ApiResponse<KycCaseResponse> get(@PathVariable UUID caseId) {
        return ApiResponse.ok(kycCaseService.get(caseId));
    }

    @PostMapping("/kyc/cases/{caseId}/identity-check")
    @PreAuthorize("hasAnyAuthority('customer.create', 'customer.edit', 'kyc.review')")
    @Operation(summary = "Verify the primary identity document with the configured provider")
    public ApiResponse<KycCaseResponse> identityCheck(@PathVariable UUID caseId) {
        return ApiResponse.ok("Identity check recorded", kycCaseService.runIdentityCheck(caseId));
    }

    @PostMapping("/kyc/cases/{caseId}/checks")
    @PreAuthorize("hasAuthority('kyc.review')")
    @Operation(summary = "Record a manual check (e.g. watchlist screening, face match)")
    public ApiResponse<KycCaseResponse> manualCheck(@PathVariable UUID caseId,
                                                    @Valid @RequestBody ManualCheckRequest request) {
        return ApiResponse.ok("Check recorded", kycCaseService.recordManualCheck(caseId, request));
    }

    @PostMapping("/kyc/cases/{caseId}/submit")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Submit for review once the tier's evidence is captured")
    public ApiResponse<KycCaseResponse> submit(@PathVariable UUID caseId) {
        return ApiResponse.ok("KYC case submitted", kycCaseService.submit(caseId));
    }

    @PostMapping("/kyc/cases/{caseId}/return")
    @PreAuthorize("hasAuthority('kyc.review')")
    @Operation(summary = "Return to the officer for correction (not your own submission)")
    public ApiResponse<KycCaseResponse> returnForCorrection(@PathVariable UUID caseId,
                                                            @Valid @RequestBody KycNoteRequest request) {
        return ApiResponse.ok("KYC case returned", kycCaseService.returnForCorrection(caseId, request));
    }

    @PostMapping("/kyc/cases/{caseId}/approve")
    @PreAuthorize("hasAuthority('kyc.approve')")
    @Operation(summary = "Approve (four-eyes): verifies the customer at the tier and activates a new customer")
    public ApiResponse<KycCaseResponse> approve(@PathVariable UUID caseId,
                                                @Valid @RequestBody KycDecisionRequest request) {
        return ApiResponse.ok("KYC approved", kycCaseService.approve(caseId, request));
    }

    @PostMapping("/kyc/cases/{caseId}/reject")
    @PreAuthorize("hasAuthority('kyc.approve')")
    @Operation(summary = "Reject (four-eyes)")
    public ApiResponse<KycCaseResponse> reject(@PathVariable UUID caseId, @Valid @RequestBody KycNoteRequest request) {
        return ApiResponse.ok("KYC rejected", kycCaseService.reject(caseId, request));
    }

    @PostMapping("/kyc/cases/{caseId}/cancel")
    @PreAuthorize(CAPTURE)
    @Operation(summary = "Withdraw a case that has not been submitted")
    public ApiResponse<KycCaseResponse> cancel(@PathVariable UUID caseId, @Valid @RequestBody KycNoteRequest request) {
        return ApiResponse.ok("KYC case cancelled", kycCaseService.cancel(caseId, request));
    }
}
