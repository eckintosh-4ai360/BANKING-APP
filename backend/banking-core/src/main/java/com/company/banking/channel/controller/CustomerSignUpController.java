package com.company.banking.channel.controller;

import com.company.banking.channel.dto.OnboardingDtos;
import com.company.banking.channel.service.CustomerOnboardingService;
import com.company.banking.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A customer's sign-up in the app, for staff reviewing them (their risk profile answers and progress).
 */
@RestController
@RequestMapping("/api/v1/customers/{customerId}/sign-up")
@RequiredArgsConstructor
@Tag(name = "Customer sign-up")
public class CustomerSignUpController {

    private final CustomerOnboardingService onboardingService;

    @GetMapping
    @PreAuthorize("hasAuthority('customer.view')")
    @Operation(summary = "How the customer signed up in the app: risk profile answers and progress")
    public ApiResponse<OnboardingDtos.SignUpRecord> get(@PathVariable UUID customerId) {
        return ApiResponse.ok(onboardingService.forStaff(customerId));
    }
}
