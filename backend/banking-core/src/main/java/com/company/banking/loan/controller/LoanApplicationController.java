package com.company.banking.loan.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanApplicationService;
import com.company.banking.loan.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Loan applications through their workflow, with guarantors and collateral, up to disbursement.
 */
@Validated
@RestController
@RequestMapping("/api/v1/loan-applications")
@RequiredArgsConstructor
@Tag(name = "Loan applications")
public class LoanApplicationController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotency-Replayed";
    private static final String VIEW = "hasAuthority('loan.view')";

    private final LoanApplicationService applicationService;
    private final LoanService loanService;

    @GetMapping
    @PreAuthorize(VIEW)
    @Operation(summary = "Loan applications in the caller's branches")
    public ApiResponse<PageResponse<LoanDtos.Application>> search(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false)
            @Pattern(regexp = "^(DRAFT|SUBMITTED|ASSESSED|RECOMMENDED|APPROVED|REJECTED|WITHDRAWN|DISBURSED)$")
            String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(applicationService.search(customerId, status,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Start an application on a product's published terms (the caller is the loan officer)")
    public ApiResponse<LoanDtos.ApplicationDetail> create(@Valid @RequestBody LoanDtos.NewApplication request) {
        return ApiResponse.ok("Application created", applicationService.create(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    @Operation(summary = "An application with its workflow, guarantors and collateral")
    public ApiResponse<LoanDtos.ApplicationDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(applicationService.get(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Change a draft application")
    public ApiResponse<LoanDtos.ApplicationDetail> edit(@PathVariable UUID id,
                                                        @Valid @RequestBody LoanDtos.EditApplication request) {
        return ApiResponse.ok("Application updated", applicationService.edit(id, request));
    }

    @GetMapping("/{id}/schedule-preview")
    @PreAuthorize(VIEW)
    @Operation(summary = "The schedule the application's loan would have if disbursed today")
    public ApiResponse<LoanDtos.SchedulePreview> preview(@PathVariable UUID id) {
        return ApiResponse.ok(applicationService.preview(id));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Submit a draft for assessment")
    public ApiResponse<LoanDtos.ApplicationDetail> submit(@PathVariable UUID id,
                                                          @Valid @RequestBody LoanDtos.Step request) {
        return ApiResponse.ok("Application submitted", applicationService.submit(id, request));
    }

    @PostMapping("/{id}/assess")
    @PreAuthorize("hasAuthority('loan.assess')")
    @Operation(summary = "Record the credit assessment")
    public ApiResponse<LoanDtos.ApplicationDetail> assess(@PathVariable UUID id,
                                                          @Valid @RequestBody LoanDtos.Assess request) {
        return ApiResponse.ok("Assessment recorded", applicationService.assess(id, request));
    }

    @PostMapping("/{id}/recommend")
    @PreAuthorize("hasAuthority('loan.recommend')")
    @Operation(summary = "Recommend an assessed application for approval (not by its loan officer)")
    public ApiResponse<LoanDtos.ApplicationDetail> recommend(@PathVariable UUID id,
                                                             @Valid @RequestBody LoanDtos.Step request) {
        return ApiResponse.ok("Application recommended", applicationService.recommend(id, request));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('loan.approve')")
    @Operation(summary = "Approve on given terms; above the product's threshold a second approver follows")
    public ApiResponse<LoanDtos.ApplicationDetail> approve(@PathVariable UUID id,
                                                           @Valid @RequestBody LoanDtos.Approve request) {
        return ApiResponse.ok("Application approved", applicationService.approve(id, request));
    }

    @PostMapping("/{id}/second-approval")
    @PreAuthorize("hasAuthority('loan.approve')")
    @Operation(summary = "Second approval of a large loan (someone other than the first approver)")
    public ApiResponse<LoanDtos.ApplicationDetail> secondApprove(@PathVariable UUID id,
                                                                 @Valid @RequestBody LoanDtos.Step request) {
        return ApiResponse.ok("Application approved", applicationService.secondApprove(id, request));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAuthority('loan.approve')")
    @Operation(summary = "Reject an application, with the reason")
    public ApiResponse<LoanDtos.ApplicationDetail> reject(@PathVariable UUID id,
                                                          @Valid @RequestBody LoanDtos.Reject request) {
        return ApiResponse.ok("Application rejected", applicationService.reject(id, request));
    }

    @PostMapping("/{id}/withdraw")
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Withdraw an application the customer no longer wants")
    public ApiResponse<LoanDtos.ApplicationDetail> withdraw(@PathVariable UUID id,
                                                            @Valid @RequestBody LoanDtos.Step request) {
        return ApiResponse.ok("Application withdrawn", applicationService.withdraw(id, request));
    }

    @PostMapping("/{id}/guarantors")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Add a guarantor")
    public ApiResponse<LoanDtos.ApplicationDetail> addGuarantor(@PathVariable UUID id,
                                                                @Valid @RequestBody LoanDtos.NewGuarantor request) {
        return ApiResponse.ok("Guarantor added", applicationService.addGuarantor(id, request));
    }

    @PostMapping("/{id}/guarantors/{guarantorId}/verify")
    @PreAuthorize("hasAuthority('loan.assess')")
    @Operation(summary = "Confirm a guarantor (not by the loan officer)")
    public ApiResponse<LoanDtos.ApplicationDetail> verifyGuarantor(@PathVariable UUID id,
                                                                   @PathVariable UUID guarantorId) {
        return ApiResponse.ok("Guarantor verified", applicationService.verifyGuarantor(id, guarantorId));
    }

    @PostMapping("/{id}/collateral")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan.create')")
    @Operation(summary = "Pledge collateral")
    public ApiResponse<LoanDtos.ApplicationDetail> addCollateral(@PathVariable UUID id,
                                                                 @Valid @RequestBody LoanDtos.NewCollateral request) {
        return ApiResponse.ok("Collateral added", applicationService.addCollateral(id, request));
    }

    @PostMapping("/{id}/collateral/{collateralId}/verify")
    @PreAuthorize("hasAuthority('loan.assess')")
    @Operation(summary = "Confirm collateral and its valuation (not by the loan officer)")
    public ApiResponse<LoanDtos.ApplicationDetail> verifyCollateral(@PathVariable UUID id,
                                                                    @PathVariable UUID collateralId) {
        return ApiResponse.ok("Collateral verified", applicationService.verifyCollateral(id, collateralId));
    }

    @PostMapping("/{id}/collateral/{collateralId}/release")
    @PreAuthorize("hasAuthority('loan.approve')")
    @Operation(summary = "Hand collateral back once it secures nothing")
    public ApiResponse<LoanDtos.ApplicationDetail> releaseCollateral(@PathVariable UUID id,
                                                                     @PathVariable UUID collateralId) {
        return ApiResponse.ok("Collateral released", applicationService.releaseCollateral(id, collateralId));
    }

    @PostMapping("/{id}/disburse")
    @PreAuthorize("hasAuthority('loan.disburse')")
    @Operation(summary = "Disburse an approved application into the borrower's account (not by anyone who"
            + " recommended or approved it)")
    public ResponseEntity<ApiResponse<LoanDtos.LoanDetail>> disburse(
            @Parameter(description = "Unique per intended disbursement; reuse it only to retry")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody LoanDtos.Disburse request) {
        Result<LoanDtos.LoanDetail> result = loanService.disburse(idempotencyKey, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(REPLAYED, Boolean.toString(result.replayed()))
                .body(ApiResponse.ok("Loan disbursed", result.response()));
    }
}
