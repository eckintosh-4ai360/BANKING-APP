package com.company.banking.channel.controller;

import com.company.banking.channel.dto.ChannelDtos;
import com.company.banking.channel.service.CustomerAuthService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.iam.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Customer sign-in for the mobile app. Every call identifies the app installation with the {@code X-Device-Id}
 * header. Token refresh and sign-out use the shared {@code /api/v1/auth/token/refresh} and {@code /api/v1/auth/logout}.
 */
@RestController
@RequestMapping("/api/v1/customer/auth")
@RequiredArgsConstructor
@Tag(name = "Customer sign-in")
public class CustomerAuthController {

    private final CustomerAuthService authService;

    @PostMapping("/login")
    @Operation(summary = "Sign in with phone number and password; an untrusted device gets a texted code first")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody ChannelDtos.Login request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/device/verify")
    @Operation(summary = "Trust this device with the code texted at sign-in")
    public ApiResponse<TokenResponse> verifyDevice(@Valid @RequestBody ChannelDtos.DeviceVerification request) {
        return ApiResponse.ok(authService.verifyDevice(request));
    }

    @PostMapping("/activation")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Set up mobile banking: a code is texted to the phone on file when the details match")
    public ApiResponse<ChannelDtos.CodeSent> startActivation(@Valid @RequestBody ChannelDtos.Activation request) {
        return ApiResponse.ok("If the details match, a code was sent", authService.startActivation(request));
    }

    @PostMapping("/activation/complete")
    @Operation(summary = "Choose a password and transaction PIN with the texted code; this device becomes trusted")
    public ApiResponse<TokenResponse> completeActivation(
            @Valid @RequestBody ChannelDtos.ActivationCompletion request) {
        return ApiResponse.ok("Mobile banking is set up", authService.completeActivation(request));
    }

    @PostMapping("/sign-up")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Sign up for mobile banking as a new customer: texts a code to the phone number")
    public ApiResponse<ChannelDtos.CodeSent> startSignUp(@Valid @RequestBody ChannelDtos.SignUp request) {
        return ApiResponse.ok("If the number can sign up, a code is on its way", authService.startSignUp(request));
    }

    @PostMapping("/sign-up/complete")
    @Operation(summary = "Register with the texted code, a password and a transaction PIN, and sign in")
    public ApiResponse<TokenResponse> completeSignUp(@Valid @RequestBody ChannelDtos.SignUpCompletion request) {
        return ApiResponse.ok("Signed up; finish your details in the app", authService.completeSignUp(request));
    }

    @PostMapping("/password/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Forgot password: a code is texted when the number has mobile banking")
    public ApiResponse<ChannelDtos.CodeSent> startPasswordReset(
            @Valid @RequestBody ChannelDtos.PasswordReset request) {
        return ApiResponse.ok("If the number has mobile banking, a code was sent",
                authService.startPasswordReset(request));
    }

    @PostMapping("/password/reset/complete")
    @Operation(summary = "Set a new password with the texted code and the transaction PIN; every session ends")
    public ApiResponse<Void> completePasswordReset(
            @Valid @RequestBody ChannelDtos.PasswordResetCompletion request) {
        authService.completePasswordReset(request);
        return ApiResponse.ok("Password reset. Sign in with the new password.", null);
    }

    @PostMapping("/password")
    @Operation(summary = "Change the password (signed in); other sessions end")
    public ApiResponse<TokenResponse> changePassword(@Valid @RequestBody ChannelDtos.PasswordChange request) {
        return ApiResponse.ok("Password changed", authService.changePassword(request));
    }
}
