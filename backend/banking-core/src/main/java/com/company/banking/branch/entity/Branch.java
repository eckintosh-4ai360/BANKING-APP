package com.company.banking.branch.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Entity
@Table(name = "branch")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Branch extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "code", nullable = false, updatable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "branch_type", nullable = false, updatable = false, length = 20)
    private BranchType branchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BranchStatus status;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "email", length = 254)
    private String email;

    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "region", length = 100)
    private String region;

    @Column(name = "digital_address", length = 20)
    private String digitalAddress;

    @Column(name = "opened_on")
    private LocalDate openedOn;

    @Column(name = "closed_on")
    private LocalDate closedOn;

    public Branch(UUID id, UUID tenantId, String code, String name, BranchType branchType, LocalDate openedOn) {
        this.id = id;
        this.tenantId = tenantId;
        this.code = code;
        this.name = name;
        this.branchType = branchType;
        this.status = BranchStatus.ACTIVE;
        this.openedOn = openedOn;
    }

    public void updateDetails(String name, String phone, String email, String addressLine1, String addressLine2,
                              String city, String region, String digitalAddress, LocalDate openedOn) {
        this.name = name;
        this.phone = phone;
        this.email = email;
        this.addressLine1 = addressLine1;
        this.addressLine2 = addressLine2;
        this.city = city;
        this.region = region;
        this.digitalAddress = digitalAddress;
        this.openedOn = openedOn;
    }

    public void changeStatus(BranchStatus target, LocalDate today) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Branch cannot move from " + status + " to " + target + ".");
        }
        if (target == BranchStatus.CLOSED && branchType == BranchType.HEAD_OFFICE) {
            throw new BankingException(CommonErrorCode.BUSINESS_RULE_VIOLATION, "The head office cannot be closed.");
        }
        this.status = target;
        this.closedOn = target == BranchStatus.CLOSED ? today : null;
    }

    public boolean isActive() {
        return status == BranchStatus.ACTIVE;
    }
}
