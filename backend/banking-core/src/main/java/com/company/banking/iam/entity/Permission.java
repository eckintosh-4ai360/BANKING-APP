package com.company.banking.iam.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Catalog entry seeded by migration; read-only to the application.
 */
@Getter
@Entity
@Immutable
@Table(name = "permission")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Permission {

    @Id
    @Column(name = "code", length = 64)
    private String code;

    @Column(name = "module", nullable = false, length = 40)
    private String module;

    @Column(name = "description", nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 10)
    private PermissionScope scope;

    @Column(name = "sensitive", nullable = false)
    private boolean sensitive;
}
