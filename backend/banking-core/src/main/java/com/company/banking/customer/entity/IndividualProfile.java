package com.company.banking.customer.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.domain.Persistable;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Personal details of an individual customer (1:1 with the customer; versioned through the customer row).
 */
@Getter
@Setter
@Entity
@Table(name = "individual_profile")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IndividualProfile implements Persistable<UUID> {

    @Id
    @Setter(AccessLevel.NONE)
    @Column(name = "customer_id")
    private UUID customerId;

    @Setter(AccessLevel.NONE)
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "title", length = 20)
    private String title;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "gender", length = 20)
    private String gender;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @Column(name = "marital_status", length = 20)
    private String maritalStatus;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "employer_name", length = 150)
    private String employerName;

    @Column(name = "employment_status", length = 20)
    private String employmentStatus;

    @Column(name = "monthly_income_band", length = 30)
    private String monthlyIncomeBand;

    @Column(name = "tax_id_encrypted", length = 512)
    private String taxIdEncrypted;

    @Column(name = "tax_id_blind_index", length = 64)
    private String taxIdBlindIndex;

    @Column(name = "tax_id_masked", length = 40)
    private String taxIdMasked;

    @Transient
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    private boolean newEntity = true;

    public IndividualProfile(UUID customerId, UUID tenantId) {
        this.customerId = customerId;
        this.tenantId = tenantId;
    }

    public void setTaxId(String encrypted, String blindIndex, String masked) {
        this.taxIdEncrypted = encrypted;
        this.taxIdBlindIndex = blindIndex;
        this.taxIdMasked = masked;
    }

    public String fullName() {
        return middleName == null || middleName.isBlank()
                ? firstName + " " + lastName
                : firstName + " " + middleName + " " + lastName;
    }

    @Override
    public UUID getId() {
        return customerId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    void markPersisted() {
        newEntity = false;
    }
}
