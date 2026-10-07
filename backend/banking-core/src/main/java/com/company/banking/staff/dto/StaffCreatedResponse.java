package com.company.banking.staff.dto;

import com.company.banking.iam.dto.IssuedCredential;

/**
 * Returned once on creation. The temporary password is not retrievable afterwards.
 */
public record StaffCreatedResponse(StaffResponse staff, IssuedCredential credential) {
}
