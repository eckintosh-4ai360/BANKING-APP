package com.company.banking.operations.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.operations.dto.BusinessDateResponse;
import com.company.banking.operations.service.BusinessCalendarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The business date every signed-in staff member works on (tellers, officers and managers all need it). No
 * permission is required, deliberately: it reveals nothing but the institution's own date and calendar.
 */
@RestController
@RequestMapping("/api/v1/operations/business-date")
@RequiredArgsConstructor
@Tag(name = "Operations")
public class BusinessDateController {

    private final BusinessCalendarService calendarService;

    @GetMapping
    @Operation(summary = "The current business date, the next one and the working week")
    public ApiResponse<BusinessDateResponse> businessDate() {
        return ApiResponse.ok(calendarService.businessDate());
    }
}
