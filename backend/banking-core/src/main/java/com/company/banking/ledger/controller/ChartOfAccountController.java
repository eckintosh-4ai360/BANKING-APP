package com.company.banking.ledger.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.ledger.dto.ChartOfAccountResponse;
import com.company.banking.ledger.dto.CreateChartOfAccountRequest;
import com.company.banking.ledger.dto.UpdateChartOfAccountRequest;
import com.company.banking.ledger.service.ChartOfAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/chart-of-accounts")
@RequiredArgsConstructor
@Tag(name = "Ledger")
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('ledger.view', 'settings.view', 'settings.manage', 'product.manage')")
    @Operation(summary = "The institution's chart of accounts, ordered by code")
    public ApiResponse<List<ChartOfAccountResponse>> list() {
        return ApiResponse.ok(chartOfAccountService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Add a GL account under a header account")
    public ApiResponse<ChartOfAccountResponse> create(@Valid @RequestBody CreateChartOfAccountRequest request) {
        return ApiResponse.ok("GL account created", chartOfAccountService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Rename a GL account, change whether manual journals may use it, or (de)activate it")
    public ApiResponse<ChartOfAccountResponse> update(@PathVariable UUID id,
                                                      @Valid @RequestBody UpdateChartOfAccountRequest request) {
        return ApiResponse.ok("GL account updated", chartOfAccountService.update(id, request));
    }
}
