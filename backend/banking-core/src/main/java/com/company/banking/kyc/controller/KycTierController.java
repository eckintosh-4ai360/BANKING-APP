package com.company.banking.kyc.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.kyc.dto.KycTierResponse;
import com.company.banking.kyc.dto.UpdateKycTierRequest;
import com.company.banking.kyc.service.KycTierService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kyc/tiers")
@RequiredArgsConstructor
@Tag(name = "KYC configuration")
public class KycTierController {

    private final KycTierService kycTierService;

    @GetMapping
    @PreAuthorize("hasAuthority('kyc.view')")
    @Operation(summary = "KYC tiers and the evidence each requires")
    public ApiResponse<List<KycTierResponse>> list() {
        return ApiResponse.ok(kycTierService.list());
    }

    @PutMapping("/{code}")
    @PreAuthorize("hasAuthority('settings.manage')")
    @Operation(summary = "Change a tier's requirements")
    public ApiResponse<KycTierResponse> update(@PathVariable String code,
                                               @Valid @RequestBody UpdateKycTierRequest request) {
        return ApiResponse.ok("KYC tier updated", kycTierService.update(code, request));
    }
}
