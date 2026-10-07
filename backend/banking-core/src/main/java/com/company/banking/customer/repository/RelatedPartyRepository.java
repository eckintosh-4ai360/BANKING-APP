package com.company.banking.customer.repository;

import com.company.banking.customer.entity.RelatedParty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RelatedPartyRepository extends JpaRepository<RelatedParty, UUID> {

    List<RelatedParty> findByTenantIdAndCustomerIdOrderByCreatedAt(UUID tenantId, UUID customerId);

    Optional<RelatedParty> findByTenantIdAndCustomerIdAndId(UUID tenantId, UUID customerId, UUID id);
}
