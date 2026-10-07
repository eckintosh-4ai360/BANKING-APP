package com.company.banking.customer.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.customer.dto.IdentificationTypeRequest;
import com.company.banking.customer.dto.IdentificationTypeResponse;
import com.company.banking.customer.service.IdentificationTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/kyc/identification-types")
@RequiredArgsConstructor
@Tag(name = "KYC configuration")
public class IdentificationTypeController {

    private final IdentificationTypeService identificationTypeService;

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "Identity document types accepted by the institution")
    public ApiResponse<List<IdentificationTypeResponse>> list() {
        return ApiResponse.ok(identificationTypeService.list());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Add an identity document type")
    public ApiResponse<IdentificationTypeResponse> create(@Valid @RequestBody IdentificationTypeRequest request) {
        return ApiResponse.ok("Identification type created", identificationTypeService.create(request));
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Change an identity document type")
    public ApiResponse<IdentificationTypeResponse> update(@PathVariable String code,
                                                          @Valid @RequestBody IdentificationTypeRequest request) {
        return ApiResponse.ok("Identification type updated", identificationTypeService.update(code, request));
    }
}
