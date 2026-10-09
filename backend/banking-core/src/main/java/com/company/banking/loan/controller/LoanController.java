package com.company.banking.loan.controller;

import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanCollectionsService;
import com.company.banking.loan.service.LoanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Disbursed loans: schedules, balances, payoff quotes, repayments, collections, restructure, write-off and
 * recoveries.
 */
@Validated
@RestController
@RequestMapping("/api/v1/loans")
@RequiredArgsConstructor
@Tag(name = "Loans")
public class LoanController {

    private static final String VIEW = "hasAuthority('loan.view')";

    private final LoanService loanService;
    private final LoanCollectionsService collectionsService;

    @GetMapping
    @PreAuthorize(VIEW)
    @Operation(summary = "Loans in the caller's branches")
    public ApiResponse<PageResponse<LoanDtos.Loan>> search(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|CLOSED|WRITTEN_OFF)$") String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(loanService.search(customerId, status, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    @Operation(summary = "A loan with its schedule, repayments and today's payoff amount")
    public ApiResponse<LoanDtos.LoanDetail> get(@PathVariable UUID id) {
        return ApiResponse.ok(loanService.get(id));
    }

    @GetMapping("/{id}/payoff")
    @PreAuthorize(VIEW)
    @Operation(summary = "What settles the loan today (unearned interest waived)")
    public ApiResponse<LoanDtos.Payoff> payoff(@PathVariable UUID id) {
        return ApiResponse.ok(loanService.payoff(id));
    }

    @PostMapping("/{id}/repayments")
    @PreAuthorize("hasAuthority('loan.repay')")
    @Operation(summary = "Take a repayment from the borrower's account or in cash at the caller's till")
    public ResponseEntity<ApiResponse<LoanDtos.RepaymentReceipt>> repay(
            @Parameter(description = "Unique per intended repayment; reuse it only to retry")
            @RequestHeader(name = LoanApplicationController.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody LoanDtos.Repay request) {
        Result<LoanDtos.RepaymentReceipt> result = loanService.repay(idempotencyKey, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(LoanApplicationController.REPLAYED, Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(result.response().settled() ? "Loan repaid in full" : "Repayment received",
                        result.response()));
    }

    @GetMapping("/arrears")
    @PreAuthorize(VIEW)
    @Operation(summary = "The collections queue: active loans in arrears in the caller's branches, longest first")
    public ApiResponse<PageResponse<LoanDtos.ArrearsItem>> arrears(
            @RequestParam(defaultValue = "1") @Min(1) @Max(3650) int minDays,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(collectionsService.queue(minDays, PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/{id}/collection-activities")
    @PreAuthorize(VIEW)
    @Operation(summary = "Calls, visits and promises to pay recorded on the loan, latest first")
    public ApiResponse<List<LoanDtos.CollectionActivity>> activities(@PathVariable UUID id) {
        return ApiResponse.ok(collectionsService.activities(id));
    }

    @PostMapping("/{id}/collection-activities")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('loan.collect')")
    @Operation(summary = "Record a call, visit, message or promise to pay")
    public ApiResponse<LoanDtos.CollectionActivity> recordActivity(@PathVariable UUID id,
                                                                   @Valid @RequestBody LoanDtos.NewActivity request) {
        return ApiResponse.ok("Activity recorded", collectionsService.record(id, request));
    }

    @PostMapping("/{id}/restructure")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('loan.restructure')")
    @Operation(summary = "Ask for the principal still owed to be rescheduled; a checker approves it")
    public ApiResponse<ApprovalResponse> restructure(@PathVariable UUID id,
                                                     @Valid @RequestBody LoanDtos.Restructure request) {
        return ApiResponse.ok("Waiting for approval", loanService.requestRestructure(id, request));
    }

    @PostMapping("/{id}/write-off")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAuthority('loan.writeoff')")
    @Operation(summary = "Ask for the loan to be written off against its provision; a checker approves it")
    public ApiResponse<ApprovalResponse> writeOff(@PathVariable UUID id,
                                                  @Valid @RequestBody LoanDtos.WriteOff request) {
        return ApiResponse.ok("Waiting for approval", loanService.requestWriteOff(id, request));
    }

    @PostMapping("/{id}/recoveries")
    @PreAuthorize("hasAuthority('loan.repay')")
    @Operation(summary = "Take money recovered on a written-off loan, from the borrower's account or in cash")
    public ResponseEntity<ApiResponse<LoanDtos.RecoveryReceipt>> recover(
            @Parameter(description = "Unique per intended recovery; reuse it only to retry")
            @RequestHeader(name = LoanApplicationController.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody LoanDtos.Repay request) {
        Result<LoanDtos.RecoveryReceipt> result = loanService.recover(idempotencyKey, id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .header(LoanApplicationController.REPLAYED, Boolean.toString(result.replayed()))
                .body(ApiResponse.ok("Recovery received", result.response()));
    }
}
