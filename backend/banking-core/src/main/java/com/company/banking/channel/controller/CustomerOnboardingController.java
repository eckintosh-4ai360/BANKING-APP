package com.company.banking.channel.controller;

import com.company.banking.channel.dto.OnboardingDtos;
import com.company.banking.channel.service.CustomerOnboardingService;
import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Signing up in the app, once registered: the stages, capturing details and documents, submitting them for review
 * and opening the first account.
 */
@RestController
@RequestMapping("/api/v1/customer/onboarding")
@RequiredArgsConstructor
@Tag(name = "Customer sign-up")
public class CustomerOnboardingController {

    private final CustomerOnboardingService onboardingService;

    @GetMapping
    @Operation(summary = "Where I am in signing up, with what I have entered so far")
    public ApiResponse<OnboardingDtos.Progress> progress() {
        return ApiResponse.ok(onboardingService.progress());
    }

    @GetMapping("/id-types")
    @Operation(summary = "The identity documents the institution accepts")
    public ApiResponse<List<OnboardingDtos.IdType>> idTypes() {
        return ApiResponse.ok(onboardingService.idTypes());
    }

    @PutMapping("/personal")
    @Operation(summary = "My personal details")
    public ApiResponse<OnboardingDtos.Progress> savePersonal(@Valid @RequestBody OnboardingDtos.Personal request) {
        return ApiResponse.ok("Saved", onboardingService.savePersonal(request));
    }

    @PutMapping("/employment")
    @Operation(summary = "My work or business")
    public ApiResponse<OnboardingDtos.Progress> saveEmployment(
            @Valid @RequestBody OnboardingDtos.Employment request) {
        return ApiResponse.ok("Saved", onboardingService.saveEmployment(request));
    }

    @PutMapping("/identification")
    @Operation(summary = "My identity document (replaces the one entered before)")
    public ApiResponse<OnboardingDtos.Progress> saveIdentification(
            @Valid @RequestBody OnboardingDtos.Identification request) {
        return ApiResponse.ok("Saved", onboardingService.saveIdentification(request));
    }

    @PostMapping(path = "/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload a photo or scan: identity document, selfie, signature or proof of address"
            + " (JPEG, PNG or PDF)")
    public ApiResponse<OnboardingDtos.Progress> uploadDocument(
            @RequestParam @Pattern(regexp = "^(ID_FRONT|ID_BACK|SELFIE|SIGNATURE|PROOF_OF_ADDRESS)$")
            String documentType,
            @RequestPart("file") MultipartFile file) {
        try {
            return ApiResponse.ok("Uploaded", onboardingService.uploadDocument(documentType,
                    file.getOriginalFilename(), file.getBytes()));
        } catch (IOException ex) {
            throw new BankingException(CommonErrorCode.MALFORMED_REQUEST, "The uploaded file could not be read.");
        }
    }

    @PutMapping("/address")
    @Operation(summary = "Where I live")
    public ApiResponse<OnboardingDtos.Progress> saveAddress(@Valid @RequestBody OnboardingDtos.Address request) {
        return ApiResponse.ok("Saved", onboardingService.saveAddress(request));
    }

    @PutMapping("/next-of-kin")
    @Operation(summary = "My next of kin")
    public ApiResponse<OnboardingDtos.Progress> saveNextOfKin(@Valid @RequestBody OnboardingDtos.NextOfKin request) {
        return ApiResponse.ok("Saved", onboardingService.saveNextOfKin(request));
    }

    @PutMapping("/risk-profile")
    @Operation(summary = "What the account is for and where my money comes from")
    public ApiResponse<OnboardingDtos.Progress> saveRiskProfile(
            @Valid @RequestBody OnboardingDtos.RiskProfile request) {
        return ApiResponse.ok("Saved", onboardingService.saveRiskProfile(request));
    }

    @PostMapping("/submit")
    @Operation(summary = "Submit my details for review")
    public ApiResponse<OnboardingDtos.Progress> submit() {
        return ApiResponse.ok("Submitted for review", onboardingService.submit());
    }

    @PostMapping("/account")
    @Operation(summary = "Open my first account, once my details are approved")
    public ApiResponse<OnboardingDtos.Progress> openAccount() {
        return ApiResponse.ok("Your account is open", onboardingService.openAccount());
    }
}
