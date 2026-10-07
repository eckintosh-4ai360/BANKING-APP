package com.company.banking.customer.repository;

import com.company.banking.customer.entity.IdentificationType;
import com.company.banking.customer.entity.IdentificationTypeId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IdentificationTypeRepository extends JpaRepository<IdentificationType, IdentificationTypeId> {

    List<IdentificationType> findByIdTenantIdOrderBySortOrderAscIdCodeAsc(UUID tenantId);

    boolean existsByIdTenantId(UUID tenantId);
}
