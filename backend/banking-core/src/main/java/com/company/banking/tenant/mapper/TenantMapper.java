package com.company.banking.tenant.mapper;

import com.company.banking.tenant.dto.FeatureResponse;
import com.company.banking.tenant.dto.InstitutionProfileResponse;
import com.company.banking.tenant.dto.TenantSummary;
import com.company.banking.tenant.entity.Feature;
import com.company.banking.tenant.entity.InstitutionProfile;
import com.company.banking.tenant.entity.Tenant;
import org.mapstruct.Mapper;

@Mapper
public interface TenantMapper {

    TenantSummary toSummary(Tenant tenant);

    InstitutionProfileResponse toResponse(InstitutionProfile profile);

    FeatureResponse toResponse(Feature feature);
}
