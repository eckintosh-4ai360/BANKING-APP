package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

@Embeddable
public record StaffRoleId(
        @Column(name = "staff_id") UUID staffId,
        @Column(name = "role_id") UUID roleId) implements Serializable {
}
