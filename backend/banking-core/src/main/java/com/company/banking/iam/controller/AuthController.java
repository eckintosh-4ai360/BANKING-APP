package com.company.banking.iam.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.iam.dto.ChangePasswordRequest;
import com.company.banking.iam.dto.MfaActivateRequest;
import com.company.banking.iam.dto.MfaSetupResponse;
import com.company.banking.iam.dto.MfaVerifyRequest;
import com.company.banking.iam.dto.RefreshTokenRequest;
import com.company.banking.iam.dto.StaffLoginRequest;
import com.company.banking.iam.dto.TokenResponse;
import com.company.banking.iam.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication")
public class AuthController {

    private final AuthenticationService authenticationService;

    @PostMapping("/staff/login")
    @SecurityRequirements
    @Operation(summary = "Staff login",
            description = "Returns a 10-minute access token and a single-use refresh token. A response with "
                    + "passwordChangeRequired=true grants no permissions until POST /api/v1/auth/password succeeds.")
    public ApiResponse<TokenResponse> staffLogin(@Valid @RequestBody StaffLoginRequest request) {
        return ApiResponse.ok("Signed in", authenticationService.loginStaff(request));
    }

    @PostMapping("/token/refresh")
    @SecurityRequirements
    @Operation(summary = "Exchange a refresh token for new tokens",
            description = "Refresh tokens are single-use. Presenting one twice ends the session (theft protection), "
                    + "so clients must never refresh concurrently.")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ApiResponse.ok(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/mfa/verify")
    @SecurityRequirements
    @Operation(summary = "Complete sign-in with an authenticator code",
            description = "Used when login answered mfaRequired=true. Wrong codes count towards the account lockout.")
    public ApiResponse<TokenResponse> verifyMfa(@Valid @RequestBody MfaVerifyRequest request) {
        return ApiResponse.ok("Signed in", authenticationService.verifyMfa(request));
    }

    @PostMapping("/mfa/totp/setup")
    @Operation(summary = "Start authenticator setup: returns the secret and an otpauth:// URI for a QR code")
    public ApiResponse<MfaSetupResponse> setupMfa() {
        return ApiResponse.ok(authenticationService.setupMfa());
    }

    @PostMapping("/mfa/totp/activate")
    @Operation(summary = "Confirm authenticator setup with a first code",
            description = "Ends every other session of the user and returns fresh tokens.")
    public ApiResponse<TokenResponse> activateMfa(@Valid @RequestBody MfaActivateRequest request) {
        return ApiResponse.ok("Authenticator enabled", authenticationService.activateMfa(request.code()));
    }

    @PostMapping("/logout")
    @Operation(summary = "End the current session")
    public ApiResponse<Void> logout() {
        authenticationService.logout();
        return ApiResponse.message("Signed out");
    }

    @PostMapping("/password")
    @Operation(summary = "Change own password",
            description = "Ends every session of the user and returns fresh tokens for this client.")
    public ApiResponse<TokenResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        return ApiResponse.ok("Password changed", authenticationService.changePassword(request));
    }
}
