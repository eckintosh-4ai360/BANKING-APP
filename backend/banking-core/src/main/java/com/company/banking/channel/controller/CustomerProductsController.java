package com.company.banking.channel.controller;

import com.company.banking.channel.dto.BankingDtos;
import com.company.banking.channel.dto.ProductDtos;
import com.company.banking.channel.service.CustomerProductsService;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's loans and susu plans.
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
@Tag(name = "Customer loans and savings")
public class CustomerProductsController {

    private final CustomerProductsService productsService;

    @GetMapping("/loans")
    @Operation(summary = "My loans, newest first")
    public ApiResponse<List<ProductDtos.Loan>> loans() {
        return ApiResponse.ok(productsService.loans());
    }

    @GetMapping("/loans/{id}")
    @Operation(summary = "One of my loans with its schedule, repayments and what pays it off today")
    public ApiResponse<ProductDtos.LoanDetail> loan(@PathVariable UUID id) {
        return ApiResponse.ok(productsService.loan(id));
    }

    @PostMapping("/loans/{id}/repayments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Repay from the loan's repayment account, with my PIN")
    public ApiResponse<ProductDtos.RepaymentReceipt> repay(
            @Parameter(description = "Unique per intended repayment; reuse it only to retry")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @PathVariable UUID id,
            @Valid @RequestBody BankingDtos.LoanRepayment request) {
        return ApiResponse.ok("Repayment received", productsService.repay(idempotencyKey, id, request));
    }

    @GetMapping("/susu-plans")
    @Operation(summary = "My susu plans")
    public ApiResponse<List<ProductDtos.SusuPlan>> susuPlans() {
        return ApiResponse.ok(productsService.susuPlans());
    }

    @GetMapping("/susu-plans/{id}")
    @Operation(summary = "One of my susu plans with its contributions")
    public ApiResponse<ProductDtos.SusuPlanDetail> susuPlan(@PathVariable UUID id) {
        return ApiResponse.ok(productsService.susuPlan(id));
    }
}
