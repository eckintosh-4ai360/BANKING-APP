package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanRecovery;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRecoveryRepository extends JpaRepository<LoanRecovery, UUID> {

    List<LoanRecovery> findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(UUID tenantId, UUID loanId);
}
