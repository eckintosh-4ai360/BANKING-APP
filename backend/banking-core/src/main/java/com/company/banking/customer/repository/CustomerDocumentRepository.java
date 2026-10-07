package com.company.banking.customer.repository;

import com.company.banking.customer.entity.CustomerDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerDocumentRepository extends JpaRepository<CustomerDocument, UUID> {

    List<CustomerDocument> findByTenantIdAndCustomerIdOrderByCreatedAt(UUID tenantId, UUID customerId);

    Optional<CustomerDocument> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);
}
