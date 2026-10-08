package com.company.banking.transaction.controller;

import com.company.banking.approval.dto.ApprovalResponse;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.common.idempotency.IdempotencyService.Result;
import com.company.banking.transaction.dto.CashDepositRequest;
import com.company.banking.transaction.dto.CashWithdrawalRequest;
import com.company.banking.transaction.dto.MovementResponse;
import com.company.banking.transaction.dto.ReverseTransactionRequest;
import com.company.banking.transaction.dto.TransactionResponse;
import com.company.banking.transaction.dto.TransferRequest;
import com.company.banking.transaction.service.TransactionQueryService;
import com.company.banking.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Money movement endpoints. Each one requires an {@code Idempotency-Key} header: a retry with the same key and
 * body returns the original result (header {@code Idempotency-Replayed: true}) and moves no money.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Transactions")
public class TransactionController {

    static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    static final String REPLAYED = "Idempotency-Replayed";

    private final TransactionService transactionService;
    private final TransactionQueryService queryService;

    @PostMapping("/transactions/deposits")
    @PreAuthorize("hasAuthority('transaction.create')")
    @Operation(summary = "Cash deposit into an account at a branch")
    public ResponseEntity<ApiResponse<MovementResponse>> deposit(
            @Parameter(description = "Unique per intended deposit; reuse it only to retry")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CashDepositRequest request) {
        return created("Deposit posted", transactionService.deposit(idempotencyKey, request));
    }

    @PostMapping("/transactions/withdrawals")
    @PreAuthorize("hasAuthority('transaction.create')")
    @Operation(summary = "Cash withdrawal from an account at a branch")
    public ResponseEntity<ApiResponse<MovementResponse>> withdraw(
            @Parameter(description = "Unique per intended withdrawal; reuse it only to retry")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CashWithdrawalRequest request) {
        return created("Withdrawal posted", transactionService.withdraw(idempotencyKey, request));
    }

    @PostMapping("/transactions/transfers")
    @PreAuthorize("hasAuthority('transaction.create')")
    @Operation(summary = "Transfer between two accounts of the institution")
    public ResponseEntity<ApiResponse<MovementResponse>> transfer(
            @Parameter(description = "Unique per intended transfer; reuse it only to retry")
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody TransferRequest request) {
        return created("Transfer posted", transactionService.transfer(idempotencyKey, request));
    }

    @GetMapping("/transactions/{id}")
    @PreAuthorize("hasAuthority('transaction.view')")
    @Operation(summary = "A transaction")
    public ApiResponse<TransactionResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(queryService.get(id));
    }

    @GetMapping("/accounts/{accountId}/transactions")
    @PreAuthorize("hasAuthority('transaction.view')")
    @Operation(summary = "Transactions of an account, newest first (last 90 days by default, at most a year)")
    public ApiResponse<PageResponse<TransactionResponse>> ofAccount(
            @PathVariable UUID accountId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.ofAccount(accountId, from, to,
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/transactions/{id}/reversal")
    @PreAuthorize("hasAuthority('transaction.reverse')")
    @Operation(summary = "Ask for a transaction to be reversed; a second person must approve it")
    public ResponseEntity<ApiResponse<ApprovalResponse>> requestReversal(
            @PathVariable UUID id, @Valid @RequestBody ReverseTransactionRequest request) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.ok("Reversal waiting for approval", transactionService.requestReversal(id,
                        request)));
    }

    /**
     * 201 when the money moved; 202 when the movement waits for a checker.
     */
    private static ResponseEntity<ApiResponse<MovementResponse>> created(
            String message, Result<MovementResponse> result) {
        MovementResponse response = result.response();
        return ResponseEntity.status(response.isPosted() ? HttpStatus.CREATED : HttpStatus.ACCEPTED)
                .header(REPLAYED, Boolean.toString(result.replayed()))
                .body(ApiResponse.ok(response.isPosted() ? message : "Waiting for approval", response));
    }
}
