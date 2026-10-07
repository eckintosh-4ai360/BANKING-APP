package com.company.banking.customer.repository;

import com.company.banking.customer.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findByTenantIdAndCustomerIdOrderByCreatedAt(UUID tenantId, UUID customerId);

    List<CustomerAddress> findByTenantIdAndCustomerIdAndActiveTrue(UUID tenantId, UUID customerId);

    Optional<CustomerAddress> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);
}
