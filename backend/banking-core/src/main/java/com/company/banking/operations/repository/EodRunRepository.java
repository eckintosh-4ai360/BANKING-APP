package com.company.banking.operations.repository;

import com.company.banking.operations.entity.EodRun;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EodRunRepository extends JpaRepository<EodRun, UUID> {

    Optional<EodRun> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<EodRun> findFirstByTenantIdAndStatusNot(UUID tenantId, EodRun.Status status);

    Page<EodRun> findByTenantIdOrderByBusinessDateDesc(UUID tenantId, Pageable pageable);
}
