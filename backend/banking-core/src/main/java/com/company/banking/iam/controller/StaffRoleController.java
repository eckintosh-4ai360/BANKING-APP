package com.company.banking.iam.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.iam.dto.AssignRolesRequest;
import com.company.banking.iam.dto.RoleSummary;
import com.company.banking.iam.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/staff/{staffId}/roles")
@RequiredArgsConstructor
@Tag(name = "Roles & permissions")
public class StaffRoleController {

    private final RoleService roleService;

    @PutMapping
    @PreAuthorize("hasAuthority('role.assign')")
    @Operation(summary = "Replace a staff member's roles",
            description = "Not allowed on yourself. Ends the staff member's sessions so the change applies at once.")
    public ApiResponse<List<RoleSummary>> assign(@PathVariable UUID staffId,
                                                 @Valid @RequestBody AssignRolesRequest request) {
        return ApiResponse.ok("Roles assigned", roleService.assignRoles(staffId, request.roleIds()));
    }
}
