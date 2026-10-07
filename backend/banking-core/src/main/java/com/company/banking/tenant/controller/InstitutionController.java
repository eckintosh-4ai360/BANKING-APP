package com.company.banking.tenant.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.tenant.dto.FeatureStateResponse;
import com.company.banking.tenant.dto.InstitutionProfileResponse;
import com.company.banking.tenant.dto.InstitutionResponse;
import com.company.banking.tenant.dto.UpdateBrandingRequest;
import com.company.banking.tenant.dto.UpdateFeatureRequest;
import com.company.banking.tenant.dto.UpdateInstitutionProfileRequest;
import com.company.banking.tenant.service.InstitutionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/institution")
@RequiredArgsConstructor
@Tag(name = "Institution", description = "The caller's own institution: profile, branding and features")
public class InstitutionController {

    private final InstitutionService institutionService;

    @GetMapping
    @PreAuthorize("hasAuthority('institution.view')")
    @Operation(summary = "Get the institution profile, branding and feature states")
    public ApiResponse<InstitutionResponse> get() {
        return ApiResponse.ok(institutionService.getInstitution());
    }

    @PutMapping("/profile")
    @PreAuthorize("hasAuthority('institution.manage')")
    @Operation(summary = "Update display name and contact details")
    public ApiResponse<InstitutionProfileResponse> updateProfile(
            @Valid @RequestBody UpdateInstitutionProfileRequest request) {
        return ApiResponse.ok("Institution profile updated", institutionService.updateProfile(request));
    }

    @PutMapping("/branding")
    @PreAuthorize("hasAuthority('institution.manage')")
    @Operation(summary = "Update white-label branding")
    public ApiResponse<InstitutionProfileResponse> updateBranding(@Valid @RequestBody UpdateBrandingRequest request) {
        return ApiResponse.ok("Branding updated", institutionService.updateBranding(request));
    }

    @PutMapping("/features/{code}")
    @PreAuthorize("hasAuthority('institution.manage')")
    @Operation(summary = "Enable or disable a licensed feature")
    public ApiResponse<FeatureStateResponse> setFeature(
            @PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
            @Valid @RequestBody UpdateFeatureRequest request) {
        return ApiResponse.ok("Feature updated", institutionService.setFeatureEnabled(code, request.enabled()));
    }
}
