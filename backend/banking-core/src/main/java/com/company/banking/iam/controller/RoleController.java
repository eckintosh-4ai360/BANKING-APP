package com.company.banking.iam.controller;

import com.company.banking.common.api.ApiResponse;
import com.company.banking.iam.dto.CreateRoleRequest;
import com.company.banking.iam.dto.PermissionResponse;
import com.company.banking.iam.dto.RoleResponse;
import com.company.banking.iam.dto.UpdateRoleRequest;
import com.company.banking.iam.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Roles & permissions")
public class RoleController {

    private final RoleService roleService;

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('permission.view')")
    @Operation(summary = "Permission catalog assignable to institution roles")
    public ApiResponse<List<PermissionResponse>> permissions() {
        return ApiResponse.ok(roleService.tenantPermissionCatalog());
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role.view')")
    @Operation(summary = "List roles")
    public ApiResponse<List<RoleResponse>> list() {
        return ApiResponse.ok(roleService.list());
    }

    @GetMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role.view')")
    @Operation(summary = "Get a role")
    public ApiResponse<RoleResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(roleService.get(id));
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('role.manage')")
    @Operation(summary = "Create a role")
    public ApiResponse<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
        return ApiResponse.ok("Role created", roleService.create(request));
    }

    @PutMapping("/roles/{id}")
    @PreAuthorize("hasAuthority('role.manage')")
    @Operation(summary = "Update a role (not one you hold)")
    public ApiResponse<RoleResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
        return ApiResponse.ok("Role updated", roleService.update(id, request));
    }
}
