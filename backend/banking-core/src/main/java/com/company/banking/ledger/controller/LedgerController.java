package com.company.banking.ledger.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.ledger.dto.AccountingPeriodResponse;
import com.company.banking.ledger.dto.CurrencyResponse;
import com.company.banking.ledger.dto.JournalResponse;
import com.company.banking.ledger.dto.JournalSearchCriteria;
import com.company.banking.ledger.dto.JournalSummary;
import com.company.banking.ledger.dto.ReconciliationReport;
import com.company.banking.ledger.dto.TrialBalanceResponse;
import com.company.banking.ledger.service.AccountingPeriodService;
import com.company.banking.ledger.service.CurrencyService;
import com.company.banking.ledger.service.LedgerQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
@Tag(name = "Ledger")
public class LedgerController {

    private final LedgerQueryService queryService;
    private final AccountingPeriodService periodService;
    private final CurrencyService currencyService;

    @GetMapping("/journals")
    @PreAuthorize("hasAuthority('ledger.view')")
    @Operation(summary = "Search journals by business date, source and reference (newest first)")
    public ApiResponse<PageResponse<JournalSummary>> journals(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false)
            @Pattern(regexp = "^(TRANSACTION|MANUAL|REVERSAL|EOD|LOAN|PROVISION|PERIOD_CLOSE|OPENING_BALANCE)$")
            String sourceType,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) UUID branchId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(queryService.search(
                new JournalSearchCriteria(from, to, sourceType, reference, branchId == null ? null : List.of(branchId)),
                PageRequests.of(page, size, Sort.unsorted())));
    }

    @GetMapping("/journals/{id}")
    @PreAuthorize("hasAuthority('ledger.view')")
    @Operation(summary = "A journal with all its lines")
    public ApiResponse<JournalResponse> journal(@PathVariable UUID id) {
        return ApiResponse.ok(queryService.journal(id));
    }

    @GetMapping("/trial-balance")
    @PreAuthorize("hasAuthority('ledger.view')")
    @Operation(summary = "Trial balance in one currency as of the end of a business date")
    public ApiResponse<TrialBalanceResponse> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) @Pattern(regexp = "^[A-Z]{3}$") String currency,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(queryService.trialBalance(asOf, currency, branchId));
    }

    @GetMapping("/reconciliation")
    @PreAuthorize("hasAuthority('ledger.view')")
    @Operation(summary = "Integrity check: balances equal the sum of their entries and every journal balances")
    public ApiResponse<ReconciliationReport> reconciliation() {
        return ApiResponse.ok(queryService.reconcile());
    }

    @GetMapping("/periods")
    @PreAuthorize("hasAuthority('ledger.view')")
    @Operation(summary = "Accounting periods, newest first")
    public ApiResponse<PageResponse<AccountingPeriodResponse>> periods(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(periodService.list(PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/periods/{periodStart}/close")
    @PreAuthorize("hasAuthority('ledger.post')")
    @Operation(summary = "Close an ended accounting period (oldest first); it can never be reopened")
    public ApiResponse<AccountingPeriodResponse> closePeriod(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart) {
        return ApiResponse.ok("Accounting period closed", periodService.close(periodStart));
    }

    @GetMapping("/currencies")
    @PreAuthorize("hasAnyAuthority('ledger.view', 'product.view', 'product.manage')")
    @Operation(summary = "Supported currencies and their decimal places")
    public ApiResponse<List<CurrencyResponse>> currencies() {
        return ApiResponse.ok(currencyService.list());
    }
}
