package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanApplicationStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanApplicationStepRepository extends JpaRepository<LoanApplicationStep, UUID> {

    List<LoanApplicationStep> findAllByTenantIdAndApplicationIdOrderByOccurredAtAsc(UUID tenantId, UUID applicationId);
}
