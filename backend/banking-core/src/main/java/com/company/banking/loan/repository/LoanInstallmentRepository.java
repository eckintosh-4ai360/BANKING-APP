package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanInstallment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanInstallmentRepository extends JpaRepository<LoanInstallment, LoanInstallment.Key> {

    @Query("select i from LoanInstallment i where i.tenantId = :tenantId and i.id.loanId = :loanId"
            + " and i.id.scheduleVersion = :version order by i.id.number")
    List<LoanInstallment> findSchedule(@Param("tenantId") UUID tenantId, @Param("loanId") UUID loanId,
                                       @Param("version") int version);
}
