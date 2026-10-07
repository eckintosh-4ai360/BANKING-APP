package com.company.banking.tenant.dto;

public record InstitutionProfileResponse(
        String contactEmail,
        String contactPhone,
        String supportEmail,
        String supportPhone,
        String websiteUrl,
        String addressLine1,
        String addressLine2,
        String city,
        String region,
        String digitalAddress,
        String logoUrl,
        String primaryColor,
        String secondaryColor,
        String smsSenderId,
        String emailSenderName,
        String emailSenderAddress,
        Long version) {
}
