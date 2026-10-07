package com.company.banking.iam.mapper;

import com.company.banking.iam.dto.PermissionResponse;
import com.company.banking.iam.dto.RoleResponse;
import com.company.banking.iam.dto.RoleSummary;
import com.company.banking.iam.entity.Permission;
import com.company.banking.iam.entity.Role;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(imports = List.class)
public interface IamMapper {

    PermissionResponse toResponse(Permission permission);

    @Mapping(target = "permissions", expression = "java(List.copyOf(role.permissionCodes()))")
    RoleResponse toResponse(Role role);

    RoleSummary toSummary(Role role);
}
