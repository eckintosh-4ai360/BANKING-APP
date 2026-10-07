package com.company.banking.customer.repository;

import com.company.banking.customer.entity.NextOfKin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NextOfKinRepository extends JpaRepository<NextOfKin, UUID> {

    List<NextOfKin> findByTenantIdAndCustomerIdOrderByCreatedAt(UUID tenantId, UUID customerId);

    Optional<NextOfKin> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);

    boolean existsByTenantIdAndCustomerIdAndActiveTrue(UUID tenantId, UUID customerId);
}
