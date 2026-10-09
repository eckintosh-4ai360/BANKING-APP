package com.company.banking.loan.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanPortfolioService;
import com.company.banking.loan.service.LoanSettingsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The loan portfolio by delinquency band, and the bands themselves.
 */
@RestController
@RequestMapping("/api/v1/loan-portfolio")
@RequiredArgsConstructor
@Tag(name = "Loan portfolio")
public class LoanPortfolioController {

    private final LoanPortfolioService portfolioService;
    private final LoanSettingsService settingsService;

    @GetMapping
    @PreAuthorize("hasAuthority('loan.view')")
    @Operation(summary = "Active loans in the caller's branches by currency and delinquency band, with PAR 30")
    public ApiResponse<List<LoanDtos.Portfolio>> portfolio() {
        return ApiResponse.ok(portfolioService.portfolio());
    }

    @GetMapping("/delinquency-bands")
    @PreAuthorize("hasAnyAuthority('loan.view', 'product.view', 'product.manage')")
    @Operation(summary = "The delinquency bands: provision rate and accrual by days past due")
    public ApiResponse<List<LoanDtos.Band>> bands() {
        return ApiResponse.ok(settingsService.bands());
    }

    @PutMapping("/delinquency-bands")
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Replace the delinquency bands; loans move to their new band at the next end-of-day")
    public ApiResponse<List<LoanDtos.Band>> replaceBands(@Valid @RequestBody LoanDtos.Bands request) {
        return ApiResponse.ok("Delinquency bands saved", settingsService.replaceBands(request));
    }
}
