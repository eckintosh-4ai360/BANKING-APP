package com.company.banking.customer.repository;

import com.company.banking.customer.entity.CustomerIdentification;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerIdentificationRepository extends JpaRepository<CustomerIdentification, UUID> {

    List<CustomerIdentification> findByTenantIdAndCustomerIdOrderByCreatedAt(UUID tenantId, UUID customerId);

    List<CustomerIdentification> findByTenantIdAndCustomerIdAndActiveTrue(UUID tenantId, UUID customerId);

    Optional<CustomerIdentification> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);

    Optional<CustomerIdentification> findByTenantIdAndIdTypeCodeAndIdNumberBlindIndexAndActiveTrue(
            UUID tenantId, String idTypeCode, String idNumberBlindIndex);
}
