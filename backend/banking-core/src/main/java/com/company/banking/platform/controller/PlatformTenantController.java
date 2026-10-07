package com.company.banking.platform.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageResponse;
import com.company.banking.platform.dto.ChangeTenantStatusRequest;
import com.company.banking.platform.dto.LicenseFeatureRequest;
import com.company.banking.platform.dto.OnboardTenantRequest;
import com.company.banking.platform.dto.OnboardTenantResponse;
import com.company.banking.platform.dto.TenantDetailsResponse;
import com.company.banking.platform.service.PlatformTenantService;
import com.company.banking.tenant.dto.FeatureResponse;
import com.company.banking.tenant.dto.TenantSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Platform institutions", description = "Institution registry, onboarding and licensing")
public class PlatformTenantController {

    private final PlatformTenantService platformTenantService;

    @GetMapping("/tenants")
    @PreAuthorize("hasAuthority('platform.tenant.view')")
    @Operation(summary = "List institutions")
    public ApiResponse<PageResponse<TenantSummary>> list(@RequestParam(required = false) Integer page,
                                                         @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(platformTenantService.list(page, size));
    }

    @GetMapping("/tenants/{id}")
    @PreAuthorize("hasAuthority('platform.tenant.view')")
    @Operation(summary = "Get an institution with its feature licences")
    public ApiResponse<TenantDetailsResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(platformTenantService.get(id));
    }

    @PostMapping("/tenants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('platform.tenant.manage')")
    @Operation(summary = "Onboard an institution",
            description = "Creates the institution, licences, default roles, head office and first administrator. "
                    + "The administrator's temporary password is returned once.")
    public ApiResponse<OnboardTenantResponse> onboard(@Valid @RequestBody OnboardTenantRequest request) {
        return ApiResponse.ok("Institution onboarded", platformTenantService.onboard(request));
    }

    @PostMapping("/tenants/{id}/status")
    @PreAuthorize("hasAuthority('platform.tenant.manage')")
    @Operation(summary = "Activate, suspend or terminate an institution",
            description = "Suspension and termination end all staff sessions of the institution immediately.")
    public ApiResponse<TenantSummary> changeStatus(@PathVariable UUID id,
                                                   @Valid @RequestBody ChangeTenantStatusRequest request) {
        return ApiResponse.ok("Institution status changed", platformTenantService.changeStatus(id, request));
    }

    @PutMapping("/tenants/{id}/features/{code}")
    @PreAuthorize("hasAuthority('platform.feature.manage')")
    @Operation(summary = "Grant or revoke a feature licence (revoking also disables it)")
    public ApiResponse<TenantDetailsResponse> license(
            @PathVariable UUID id,
            @PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9_]{1,39}$") String code,
            @Valid @RequestBody LicenseFeatureRequest request) {
        return ApiResponse.ok("Licence updated",
                platformTenantService.setFeatureLicensed(id, code, request.licensed()));
    }

    @GetMapping("/features")
    @PreAuthorize("hasAuthority('platform.tenant.view')")
    @Operation(summary = "Feature catalog")
    public ApiResponse<List<FeatureResponse>> features() {
        return ApiResponse.ok(platformTenantService.featureCatalog());
    }
}
