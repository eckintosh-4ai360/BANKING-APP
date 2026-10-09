package com.company.banking.susu.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.common.api.PageRequests;
import com.company.banking.common.api.PageResponse;
import com.company.banking.susu.dto.SusuDtos;
import com.company.banking.susu.service.SusuPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/susu")
@RequiredArgsConstructor
@Tag(name = "Susu")
public class SusuController {

    private static final String VIEW = "hasAnyAuthority('susu.view', 'susu.manage')";
    private static final String MANAGE = "hasAuthority('susu.manage')";

    private final SusuPlanService planService;

    @GetMapping("/frequencies")
    @PreAuthorize(VIEW)
    @Operation(summary = "Susu frequencies of the institution")
    public ApiResponse<List<SusuDtos.Frequency>> frequencies() {
        return ApiResponse.ok(planService.frequencies());
    }

    @PostMapping("/frequencies")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Add a susu frequency (e.g. every 2 weeks)")
    public ApiResponse<SusuDtos.Frequency> createFrequency(@Valid @RequestBody SusuDtos.NewFrequency request) {
        return ApiResponse.ok("Frequency created", planService.createFrequency(request));
    }

    @GetMapping("/plans")
    @PreAuthorize(VIEW)
    @Operation(summary = "Susu plans in the caller's branches")
    public ApiResponse<PageResponse<SusuDtos.Plan>> plans(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @Pattern(regexp = "^(ACTIVE|COMPLETED|CANCELLED)$") String status,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ApiResponse.ok(planService.search(customerId, status, PageRequests.of(page, size, Sort.unsorted())));
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(MANAGE)
    @Operation(summary = "Open a susu plan on a customer's susu account (schedules its first cycle)")
    public ApiResponse<SusuDtos.PlanDetail> open(@Valid @RequestBody SusuDtos.OpenPlan request) {
        return ApiResponse.ok("Susu plan opened", planService.open(request));
    }

    @GetMapping("/plans/{id}")
    @PreAuthorize(VIEW)
    @Operation(summary = "A susu plan with its contributions and commissions")
    public ApiResponse<SusuDtos.PlanDetail> plan(@PathVariable UUID id) {
        return ApiResponse.ok(planService.get(id));
    }

    @PostMapping("/plans/{id}/cancel")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Cancel a running susu plan")
    public ApiResponse<SusuDtos.PlanDetail> cancel(@PathVariable UUID id, @Valid @RequestBody SusuDtos.Close request) {
        return ApiResponse.ok("Susu plan cancelled", planService.cancel(id, request));
    }

    @PostMapping("/plans/{id}/contributions/{sequenceNo}/waive")
    @PreAuthorize(MANAGE)
    @Operation(summary = "Waive an expected or missed contribution")
    public ApiResponse<SusuDtos.PlanDetail> waive(@PathVariable UUID id, @PathVariable int sequenceNo,
                                                  @Valid @RequestBody SusuDtos.Waive request) {
        return ApiResponse.ok("Contribution waived", planService.waive(id, sequenceNo, request));
    }
}
