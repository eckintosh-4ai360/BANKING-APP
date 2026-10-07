package com.company.banking.staff.entity;

import com.company.banking.common.error.BankingException;
import com.company.banking.common.error.CommonErrorCode;
import com.company.banking.common.persistence.AuditableEntity;
import com.company.banking.common.security.BranchScope;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Set;
import java.util.UUID;

/**
 * An employee of an institution. Login credentials live in the IAM module under the same id.
 */
@Getter
@Entity
@Table(name = "staff")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Staff extends AuditableEntity {

    @Id
    private UUID id;

    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(name = "employee_number", nullable = false, updatable = false, length = 30)
    private String employeeNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "job_title", length = 100)
    private String jobTitle;

    @Column(name = "home_branch_id", nullable = false)
    private UUID homeBranchId;

    @Column(name = "all_branches_access", nullable = false)
    private boolean allBranchesAccess;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StaffStatus status;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    public Staff(UUID id, UUID tenantId, String employeeNumber, String firstName, String lastName, String email,
                 String phone, String jobTitle, UUID homeBranchId, boolean allBranchesAccess) {
        this.id = id;
        this.tenantId = tenantId;
        this.employeeNumber = employeeNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.jobTitle = jobTitle;
        this.homeBranchId = homeBranchId;
        this.allBranchesAccess = allBranchesAccess;
        this.status = StaffStatus.ACTIVE;
    }

    public void updateProfile(String firstName, String lastName, String email, String phone, String jobTitle) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.jobTitle = jobTitle;
    }

    public void changeAccessScope(UUID homeBranchId, boolean allBranchesAccess) {
        this.homeBranchId = homeBranchId;
        this.allBranchesAccess = allBranchesAccess;
    }

    public void changeStatus(StaffStatus target, String reason) {
        if (!status.canTransitionTo(target)) {
            throw new BankingException(CommonErrorCode.INVALID_STATE_TRANSITION,
                    "Staff cannot move from " + status + " to " + target + ".");
        }
        this.status = target;
        this.statusReason = reason;
    }

    public BranchScope branchScope() {
        return allBranchesAccess ? BranchScope.all() : BranchScope.of(Set.of(homeBranchId));
    }

    public boolean isActive() {
        return status == StaffStatus.ACTIVE;
    }
}
