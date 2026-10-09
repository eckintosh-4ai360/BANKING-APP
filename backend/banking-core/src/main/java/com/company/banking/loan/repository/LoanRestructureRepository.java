package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanRestructure;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRestructureRepository extends JpaRepository<LoanRestructure, UUID> {

    List<LoanRestructure> findAllByTenantIdAndLoanIdOrderByToVersion(UUID tenantId, UUID loanId);
}
