package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanInstallment;
import java.time.LocalDate;
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

    /** Installments of active loans' current schedules due on a date. */
    @Query("select i from LoanInstallment i, Loan l where l.tenantId = :tenantId"
            + " and l.status = com.company.banking.loan.entity.Loan.Status.ACTIVE"
            + " and i.tenantId = l.tenantId and i.id.loanId = l.id and i.id.scheduleVersion = l.scheduleVersion"
            + " and i.dueDate = :date")
    List<LoanInstallment> dueOn(@Param("tenantId") UUID tenantId, @Param("date") LocalDate date);
}
