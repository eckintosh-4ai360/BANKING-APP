package com.company.banking.tenant.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.validation.ValidationPatterns;
import com.company.banking.tenant.dto.PublicBrandingResponse;
import com.company.banking.tenant.service.InstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public/institutions")
@RequiredArgsConstructor
@Tag(name = "Public", description = "Unauthenticated endpoints used by apps before login")
public class PublicInstitutionController {

    private final InstitutionService institutionService;

    @GetMapping("/{code}/branding")
    @SecurityRequirements
    @Operation(summary = "White-label branding of an active institution")
    public ApiResponse<PublicBrandingResponse> branding(
            @PathVariable @Pattern(regexp = ValidationPatterns.TENANT_CODE) String code) {
        return ApiResponse.ok(institutionService.getPublicBranding(code));
    }
}
