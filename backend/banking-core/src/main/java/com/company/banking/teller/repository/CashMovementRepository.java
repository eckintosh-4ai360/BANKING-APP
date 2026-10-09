package com.company.banking.teller.repository;

import com.company.banking.teller.entity.CashMovement;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashMovementRepository extends JpaRepository<CashMovement, UUID>,
        JpaSpecificationExecutor<CashMovement> {

    Optional<CashMovement> findByTenantIdAndId(UUID tenantId, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from CashMovement m where m.tenantId = :tenantId and m.id = :id")
    Optional<CashMovement> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);
}
