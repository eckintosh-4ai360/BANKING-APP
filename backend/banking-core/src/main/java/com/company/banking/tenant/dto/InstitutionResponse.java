package com.company.banking.tenant.dto;

import java.util.List;

public record InstitutionResponse(
        TenantSummary institution,
        InstitutionProfileResponse profile,
        List<FeatureStateResponse> features) {
}
