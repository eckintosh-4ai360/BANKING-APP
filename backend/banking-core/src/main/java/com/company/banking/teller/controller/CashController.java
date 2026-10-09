package com.company.banking.teller.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.teller.dto.CashMovementRequest;
import com.company.banking.teller.dto.CashMovementResponse;
import com.company.banking.teller.dto.CashPositionResponse;
import com.company.banking.teller.dto.CreateDrawerRequest;
import com.company.banking.teller.dto.CreateVaultRequest;
import com.company.banking.teller.dto.DrawerResponse;
import com.company.banking.teller.dto.SupervisorDecisionRequest;
import com.company.banking.teller.dto.VaultResponse;
import com.company.banking.teller.service.CashMovementService;
import com.company.banking.teller.service.CashPointService;
import com.company.banking.teller.service.CashReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/v1/cash")
@RequiredArgsConstructor
@Tag(name = "Cash")
public class CashController {

    private final CashPointService cashPoints;
    private final CashMovementService movements;
    private final CashReconciliationService reconciliation;

    @GetMapping("/vaults")
    @PreAuthorize("hasAnyAuthority('cash.view', 'cash.manage')")
    @Operation(summary = "Vaults in the caller's branches with the cash they hold")
    public ApiResponse<List<VaultResponse>> vaults() {
        return ApiResponse.ok(cashPoints.vaults());
    }

    @PostMapping("/vaults")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash.manage')")
    @Operation(summary = "Create a branch vault for a currency")
    public ApiResponse<VaultResponse> createVault(@Valid @RequestBody CreateVaultRequest request) {
        return ApiResponse.ok("Vault created", cashPoints.createVault(request));
    }

    @GetMapping("/drawers")
    @PreAuthorize("hasAnyAuthority('cash.view', 'cash.manage', 'teller.operate')")
    @Operation(summary = "Teller drawers with their expected cash and who is working them")
    public ApiResponse<List<DrawerResponse>> drawers(@RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(cashPoints.drawers(branchId));
    }

    @PostMapping("/drawers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('cash.manage')")
    @Operation(summary = "Create a teller drawer")
    public ApiResponse<DrawerResponse> createDrawer(@Valid @RequestBody CreateDrawerRequest request) {
        return ApiResponse.ok("Drawer created", cashPoints.createDrawer(request));
    }

    @GetMapping("/positions")
    @PreAuthorize("hasAnyAuthority('cash.view', 'cash.manage', 'teller.supervise')")
    @Operation(summary = "End-of-day cash positions of the caller's vaults and drawers (latest closed date by default)")
    public ApiResponse<List<CashPositionResponse>> positions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID branchId) {
        return ApiResponse.ok(reconciliation.positions(date, branchId));
    }

    @GetMapping("/movements")
    @PreAuthorize("hasAnyAuthority('cash.view', 'cash.manage', 'teller.operate')")
    @Operation(summary = "Cash movements touching the caller's branches, newest first")
    public ApiResponse<PageResponse<CashMovementResponse>> movements(
            @RequestParam(required = false) @Pattern(regexp = "^(REQUESTED|IN_TRANSIT|COMPLETED|REJECTED|CANCELLED)$")
            String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(movements.search(status, PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyAuthority('cash.manage', 'teller.operate')")
    @Operation(summary = "Ask to move cash (vault and drawer, between branches, or with the bank)")
    public ApiResponse<CashMovementResponse> request(@Valid @RequestBody CashMovementRequest request) {
        return ApiResponse.ok("Cash movement requested", movements.request(request));
    }

    @PostMapping("/movements/{id}/approve")
    @PreAuthorize("hasAuthority('cash.manage')")
    @Operation(summary = "Approve and post a cash movement (not your own)")
    public ApiResponse<CashMovementResponse> approve(@PathVariable UUID id,
                                                     @Valid @RequestBody SupervisorDecisionRequest request) {
        return ApiResponse.ok("Cash movement approved", movements.approve(id, request));
    }

    @PostMapping("/movements/{id}/receive")
    @PreAuthorize("hasAuthority('cash.manage')")
    @Operation(summary = "Confirm cash in transit arrived at the receiving branch")
    public ApiResponse<CashMovementResponse> receive(@PathVariable UUID id,
                                                     @Valid @RequestBody SupervisorDecisionRequest request) {
        return ApiResponse.ok("Cash received", movements.receive(id, request));
    }

    @PostMapping("/movements/{id}/reject")
    @PreAuthorize("hasAuthority('cash.manage')")
    @Operation(summary = "Reject a cash movement request")
    public ApiResponse<CashMovementResponse> reject(@PathVariable UUID id,
                                                    @Valid @RequestBody SupervisorDecisionRequest request) {
        return ApiResponse.ok("Cash movement rejected", movements.reject(id, request));
    }

    @PostMapping("/movements/{id}/cancel")
    @PreAuthorize("hasAnyAuthority('cash.manage', 'teller.operate')")
    @Operation(summary = "Withdraw your own request")
    public ApiResponse<CashMovementResponse> cancel(@PathVariable UUID id,
                                                    @Valid @RequestBody SupervisorDecisionRequest request) {
        return ApiResponse.ok("Cash movement cancelled", movements.cancel(id, request));
    }
}
