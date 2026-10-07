package com.company.banking.platform.dto;

import com.company.banking.tenant.dto.FeatureStateResponse;
import com.company.banking.tenant.dto.TenantSummary;

import java.util.List;

/**
 * Registry and licensing view of an institution. Deliberately contains no customer or financial data.
 */
public record TenantDetailsResponse(TenantSummary tenant, List<FeatureStateResponse> features) {
}
