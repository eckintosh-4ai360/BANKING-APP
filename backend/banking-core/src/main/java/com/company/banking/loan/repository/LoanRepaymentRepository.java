package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanRepayment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepaymentRepository extends JpaRepository<LoanRepayment, UUID> {

    List<LoanRepayment> findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(UUID tenantId, UUID loanId);

    /** What was repaid on the loan between two business dates (inclusive). */
    @Query("select coalesce(sum(r.amount), 0) from LoanRepayment r where r.tenantId = :tenantId and r.loanId = :loanId"
            + " and r.businessDate between :from and :to")
    BigDecimal sumBetween(@Param("tenantId") UUID tenantId, @Param("loanId") UUID loanId,
                          @Param("from") LocalDate from, @Param("to") LocalDate to);
}
