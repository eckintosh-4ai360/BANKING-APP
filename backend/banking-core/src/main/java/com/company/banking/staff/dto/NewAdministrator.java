package com.company.banking.staff.dto;

/**
 * First administrator created while onboarding an institution.
 */
public record NewAdministrator(String firstName, String lastName, String email, String phone, String username) {
}
