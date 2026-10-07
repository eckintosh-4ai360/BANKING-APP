package com.company.banking.staff.mapper;

import com.company.banking.iam.dto.CredentialInfo;
import com.company.banking.iam.dto.RoleSummary;
import com.company.banking.staff.dto.StaffResponse;
import com.company.banking.staff.dto.StaffSummary;
import com.company.banking.staff.entity.Staff;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper
public interface StaffMapper {

    StaffSummary toSummary(Staff staff);

    @Mapping(target = "id", source = "staff.id")
    @Mapping(target = "createdAt", source = "staff.createdAt")
    @Mapping(target = "updatedAt", source = "staff.updatedAt")
    @Mapping(target = "version", source = "staff.version")
    StaffResponse toResponse(Staff staff, CredentialInfo login, List<RoleSummary> roles);
}
