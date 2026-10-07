package com.company.banking.customer.repository;

import com.company.banking.customer.entity.Customer;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CustomerRepository extends JpaRepository<Customer, UUID>, JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Row lock for every change to the customer aggregate, so concurrent edits of profile, identifications and KYC
     * state serialise.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Customer c where c.tenantId = :tenantId and c.id = :id")
    Optional<Customer> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
