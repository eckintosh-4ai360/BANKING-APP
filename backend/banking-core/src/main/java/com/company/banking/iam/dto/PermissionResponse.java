package com.company.banking.iam.dto;

public record PermissionResponse(String code, String module, String description, boolean sensitive) {
}
