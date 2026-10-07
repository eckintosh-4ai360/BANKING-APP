package com.company.banking.customer.entity;

import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.regex.Pattern;

/**
 * An identity document type accepted by the institution (configuration, not code).
 */
@Getter
@Entity
@Table(name = "identification_type")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentificationType extends AuditableEntity {

    @EmbeddedId
    private IdentificationTypeId id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "applies_to", nullable = false, length = 20)
    private String appliesTo;

    @Column(name = "format_regex", length = 200)
    private String formatRegex;

    @Column(name = "format_hint", length = 100)
    private String formatHint;

    @Column(name = "requires_expiry", nullable = false)
    private boolean requiresExpiry;

    @Column(name = "supports_electronic_verification", nullable = false)
    private boolean supportsElectronicVerification;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    public IdentificationType(IdentificationTypeId id) {
        this.id = id;
        this.active = true;
    }

    public void configure(String name, String appliesTo, String formatRegex, String formatHint, boolean requiresExpiry,
                          boolean supportsElectronicVerification, boolean active, int sortOrder) {
        this.name = name;
        this.appliesTo = appliesTo;
        this.formatRegex = formatRegex;
        this.formatHint = formatHint;
        this.requiresExpiry = requiresExpiry;
        this.supportsElectronicVerification = supportsElectronicVerification;
        this.active = active;
        this.sortOrder = sortOrder;
    }

    public String getCode() {
        return id.code();
    }

    public boolean appliesTo(CustomerType customerType) {
        return "ANY".equals(appliesTo) || appliesTo.equals(customerType.name());
    }

    public boolean matchesFormat(String number) {
        return formatRegex == null || Pattern.matches(formatRegex, number);
    }
}
