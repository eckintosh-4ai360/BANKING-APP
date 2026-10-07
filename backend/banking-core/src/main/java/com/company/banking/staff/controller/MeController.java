package com.company.banking.staff.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.staff.dto.MeResponse;
import com.company.banking.staff.service.StaffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
@Tag(name = "Staff")
public class MeController {

    private final StaffService staffService;

    @GetMapping
    @Operation(summary = "The signed-in staff member, roles and effective permissions")
    public ApiResponse<MeResponse> me() {
        return ApiResponse.ok(staffService.me());
    }
}
