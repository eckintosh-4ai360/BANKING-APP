package com.company.banking.staff.service;

import com.company.banking.common.tenant.TenantContext;
import com.company.banking.iam.spi.StaffDirectory;
import com.company.banking.staff.repository.StaffRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Supplies IAM with staff status and branch scope at login and token refresh.
 */
@Component
@RequiredArgsConstructor
public class StaffDirectoryAdapter implements StaffDirectory {

    private final StaffRepository staffRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<StaffAuthProfile> findAuthProfile(UUID staffId) {
        return staffRepository.findByTenantIdAndId(TenantContext.requireTenantId(), staffId)
                .map(staff -> new StaffAuthProfile(staff.getId(), staff.getStatus().name(), staff.getHomeBranchId(),
                        staff.branchScope()));
    }
}
