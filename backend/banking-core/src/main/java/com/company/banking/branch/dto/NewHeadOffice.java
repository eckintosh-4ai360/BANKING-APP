package com.company.banking.branch.dto;

/**
 * Head office created during institution onboarding.
 */
public record NewHeadOffice(String code, String name, String city, String region, String digitalAddress) {
}
