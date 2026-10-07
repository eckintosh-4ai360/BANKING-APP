package com.company.banking.iam.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformLoginRequest(
        @NotBlank @Size(max = 100) String username,
        @NotBlank @Size(max = 128) String password) {

    @Override
    public String toString() {
        return "PlatformLoginRequest[username=" + username + ", password=***]";
    }
}
