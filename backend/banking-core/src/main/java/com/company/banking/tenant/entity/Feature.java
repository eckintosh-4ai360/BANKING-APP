package com.company.banking.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * Platform catalog entry for a licensable feature (seeded by migration, read-only to the application).
 */
@Getter
@Entity
@Immutable
@Table(name = "feature")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feature {

    @Id
    @Column(name = "code", length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, length = 255)
    private String description;
}
