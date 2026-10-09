package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanRepayment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(UUID tenantId, UUID loanId);
}
