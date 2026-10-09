package com.company.banking.fieldops.service;

import com.company.banking.common.security.AuthenticatedActor;
import com.company.banking.common.security.CurrentActor;
import com.company.banking.common.security.Permissions;
import com.company.banking.common.tenant.TenantContext;
import com.company.banking.fieldops.entity.FieldOfficer;
import com.company.banking.fieldops.repository.FieldOfficerRepository;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Whose field records the caller may read: supervisors ({@code field.manage}, or {@code collection.view} without
 * being a field officer) see the officers of their branches; a field officer sees only their own.
 */
@Component
@RequiredArgsConstructor
class FieldScope {

    private final FieldOfficerRepository officers;

    /**
     * The officers the caller may read, narrowed to {@code officerId} when given (empty when it is not one of them).
     */
    Set<UUID> officers(UUID officerId) {
        AuthenticatedActor actor = CurrentActor.require();
        boolean isOfficer = officers.existsByTenantIdAndStaffId(TenantContext.requireTenantId(), actor.id());
        Set<UUID> visible;
        if (actor.hasPermission(Permissions.FIELD_MANAGE)
                || (actor.hasPermission(Permissions.COLLECTION_VIEW) && !isOfficer)) {
            visible = officers.findAllByTenantIdOrderByCreatedAtAsc(TenantContext.requireTenantId()).stream()
                    .filter(officer -> actor.branchScope().permits(officer.getBranchId()))
                    .map(FieldOfficer::getStaffId)
                    .collect(Collectors.toSet());
        } else {
            visible = isOfficer ? Set.of(actor.id()) : Set.of();
        }
        if (officerId == null) {
            return visible;
        }
        return visible.contains(officerId) ? Set.of(officerId) : Set.of();
    }
}
