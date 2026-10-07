package com.company.banking.customer.repository;

import com.company.banking.customer.entity.IndividualProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IndividualProfileRepository extends JpaRepository<IndividualProfile, UUID> {

    Optional<IndividualProfile> findByTenantIdAndCustomerId(UUID tenantId, UUID customerId);
}
