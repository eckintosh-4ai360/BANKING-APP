package com.company.banking.tenant.dto;

public record FeatureStateResponse(String code, String name, String description, boolean licensed, boolean enabled) {
}
