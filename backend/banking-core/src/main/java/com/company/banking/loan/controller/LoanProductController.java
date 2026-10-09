package com.company.banking.loan.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.loan.dto.LoanDtos;
import com.company.banking.loan.service.LoanProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/loan-products")
@RequiredArgsConstructor
@Tag(name = "Loan products")
public class LoanProductController {

    private static final String VIEW = "hasAnyAuthority('product.view', 'product.manage', 'loan.view')";
    private static final String MANAGE = "hasAuthority('product.manage')";

    private final LoanProductService productService;

    @GetMapping
    @PreAuthorize(VIEW)
    @Operation(summary = "Loan products with their versions")
    public ApiResponse<List<LoanDtos.Product>> list() {
        return ApiResponse.ok(productService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize(VIEW)
    @Operation(summary = "A loan product with its versions")
    public ApiResponse<LoanDtos.Product> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Create a loan product with a draft first version")
    public ApiResponse<LoanDtos.Product> create(@Valid @RequestBody LoanDtos.NewProduct request) {
        return ApiResponse.ok("Loan product created", productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Rename, describe, activate or deactivate a loan product")
    public ApiResponse<LoanDtos.Product> update(@PathVariable UUID id,
                                                @Valid @RequestBody LoanDtos.UpdateProduct request) {
        return ApiResponse.ok("Loan product updated", productService.update(id, request));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Start a draft version from the latest terms")
    public ApiResponse<LoanDtos.Product> createDraft(@PathVariable UUID id) {
        return ApiResponse.ok("Draft created", productService.createDraft(id));
    }

    @PutMapping("/{id}/versions/{versionId}")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Change the terms of a draft version")
    public ApiResponse<LoanDtos.Product> updateDraft(@PathVariable UUID id, @PathVariable UUID versionId,
                                                     @Valid @RequestBody LoanDtos.Terms request) {
        return ApiResponse.ok("Draft updated", productService.updateDraft(id, versionId, request));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Publish a draft; new applications get these terms, loans keep theirs")
    public ApiResponse<LoanDtos.Product> publish(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ApiResponse.ok("Version published", productService.publish(id, versionId));
    }

    @GetMapping("/{id}/schedule-preview")
    @PreAuthorize(VIEW)
    @Operation(summary = "The schedule a loan of this product would have if disbursed today")
    public ApiResponse<LoanDtos.SchedulePreview> preview(
            @PathVariable UUID id,
            @RequestParam @DecimalMin(value = "0", inclusive = false) @Digits(integer = 15, fraction = 4)
            BigDecimal amount,
            @RequestParam @Min(1) @Max(520) int installments,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate firstDueDate) {
        return ApiResponse.ok(productService.preview(id, amount, installments, firstDueDate));
    }
}
