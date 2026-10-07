package com.company.banking.iam.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.iam.dto.PlatformLoginRequest;
import com.company.banking.iam.dto.PlatformMeResponse;
import com.company.banking.iam.dto.TokenResponse;
import com.company.banking.iam.service.AuthenticationService;
import com.company.banking.iam.service.PlatformUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/platform")
@RequiredArgsConstructor
@Tag(name = "Platform authentication")
public class PlatformAuthController {

    private final AuthenticationService authenticationService;
    private final PlatformUserService platformUserService;

    @PostMapping("/auth/login")
    @SecurityRequirements
    @Operation(summary = "Platform administrator login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody PlatformLoginRequest request) {
        return ApiResponse.ok("Signed in", authenticationService.loginPlatform(request));
    }

    @GetMapping("/me")
    @Operation(summary = "Current platform administrator")
    public ApiResponse<PlatformMeResponse> me() {
        return ApiResponse.ok(platformUserService.me());
    }
}
