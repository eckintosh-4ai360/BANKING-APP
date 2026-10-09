package com.company.banking.teller.repository;

import com.company.banking.teller.entity.CashCount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CashCountRepository extends JpaRepository<CashCount, UUID> {

    List<CashCount> findAllByTenantIdAndTellerSessionIdOrderByCountedAt(UUID tenantId, UUID tellerSessionId);
}
