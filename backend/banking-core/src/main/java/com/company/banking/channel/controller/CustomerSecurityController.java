package com.company.banking.channel.controller;

import com.company.banking.channel.dto.ChannelDtos;
import com.company.banking.channel.service.CustomerSecurityService;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * The signed-in customer's profile and security: devices, sign-in history and sessions, transaction PIN.
 */
@RestController
@RequestMapping("/api/v1/customer")
@RequiredArgsConstructor
@Tag(name = "Customer security")
public class CustomerSecurityController {

    private final CustomerSecurityService securityService;

    @GetMapping("/me")
    @Operation(summary = "The signed-in customer")
    public ApiResponse<ChannelDtos.Profile> me() {
        return ApiResponse.ok(securityService.profile());
    }

    @GetMapping("/security/devices")
    @Operation(summary = "Trusted devices")
    public ApiResponse<List<ChannelDtos.Device>> devices() {
        return ApiResponse.ok(securityService.devices());
    }

    @DeleteMapping("/security/devices/{id}")
    @Operation(summary = "Stop trusting a device and end its sessions")
    public ApiResponse<Void> revokeDevice(@PathVariable UUID id) {
        securityService.revokeDevice(id);
        return ApiResponse.ok("Device removed", null);
    }

    @GetMapping("/security/sessions")
    @Operation(summary = "Sign-in history and open sessions, newest first")
    public ApiResponse<List<ChannelDtos.Session>> sessions() {
        return ApiResponse.ok(securityService.sessions());
    }

    @DeleteMapping("/security/sessions/{id}")
    @Operation(summary = "End a session")
    public ApiResponse<Void> endSession(@PathVariable UUID id) {
        securityService.endSession(id);
        return ApiResponse.ok("Session ended", null);
    }

    @PutMapping("/security/pin")
    @Operation(summary = "Change the transaction PIN")
    public ApiResponse<Void> changePin(@Valid @RequestBody ChannelDtos.PinChange request) {
        securityService.changePin(request);
        return ApiResponse.ok("PIN changed", null);
    }

    @PostMapping("/security/pin/reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Forgot or locked PIN: text a code to the phone on file")
    public ApiResponse<ChannelDtos.CodeSent> startPinReset() {
        return ApiResponse.ok("A code was sent", securityService.startPinReset());
    }

    @PostMapping("/security/pin/reset/complete")
    @Operation(summary = "Set a new PIN with the texted code and the password")
    public ApiResponse<Void> completePinReset(@Valid @RequestBody ChannelDtos.PinResetCompletion request) {
        securityService.completePinReset(request);
        return ApiResponse.ok("PIN reset", null);
    }
}
