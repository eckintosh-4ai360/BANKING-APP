package com.company.banking.product.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.product.dto.CreateProductRequest;
import com.company.banking.product.dto.ProductResponse;
import com.company.banking.product.dto.ProductTermsRequest;
import com.company.banking.product.dto.UpdateProductRequest;
import com.company.banking.product.service.ProductService;
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
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Deposit products")
public class ProductController {

    private final ProductService productService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('product.view', 'product.manage')")
    @Operation(summary = "Deposit products with their current terms and version history")
    public ApiResponse<List<ProductResponse>> list() {
        return ApiResponse.ok(productService.list());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('product.view', 'product.manage')")
    @Operation(summary = "A deposit product")
    public ApiResponse<ProductResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(productService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Create a product with a draft first version")
    public ApiResponse<ProductResponse> create(@Valid @RequestBody CreateProductRequest request) {
        return ApiResponse.ok("Product created", productService.create(request));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Rename a product or make it (un)available for new accounts")
    public ApiResponse<ProductResponse> update(@PathVariable UUID id,
                                              @Valid @RequestBody UpdateProductRequest request) {
        return ApiResponse.ok("Product updated", productService.update(id, request));
    }

    @PostMapping("/{id}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Start a new draft version from the latest terms")
    public ApiResponse<ProductResponse> createDraft(@PathVariable UUID id) {
        return ApiResponse.ok("Draft created", productService.createDraft(id));
    }

    @PutMapping("/{id}/versions/{versionId}")
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Edit the terms of a draft version")
    public ApiResponse<ProductResponse> updateDraft(@PathVariable UUID id, @PathVariable UUID versionId,
                                                    @Valid @RequestBody ProductTermsRequest request) {
        return ApiResponse.ok("Draft updated", productService.updateDraft(id, versionId, request));
    }

    @PostMapping("/{id}/versions/{versionId}/publish")
    @PreAuthorize("hasAuthority('product.manage')")
    @Operation(summary = "Publish a draft; new accounts get these terms, existing accounts keep theirs")
    public ApiResponse<ProductResponse> publish(@PathVariable UUID id, @PathVariable UUID versionId) {
        return ApiResponse.ok("Version published", productService.publish(id, versionId));
    }
}
