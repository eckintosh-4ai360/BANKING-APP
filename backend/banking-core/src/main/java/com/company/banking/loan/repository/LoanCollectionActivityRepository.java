package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanCollectionActivity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanCollectionActivityRepository extends JpaRepository<LoanCollectionActivity, UUID> {

    List<LoanCollectionActivity> findAllByTenantIdAndLoanIdOrderByCreatedAtDesc(UUID tenantId, UUID loanId);

    /** Open promises whose date has come. */
    @Query("select a from LoanCollectionActivity a where a.tenantId = :tenantId and a.loanId = :loanId"
            + " and a.promiseStatus = com.company.banking.loan.entity.LoanCollectionActivity.PromiseStatus.OPEN"
            + " and a.promisedDate <= :date order by a.createdAt")
    List<LoanCollectionActivity> findPromisesDueBy(@Param("tenantId") UUID tenantId, @Param("loanId") UUID loanId,
                                                   @Param("date") LocalDate date);

    /** The latest activity of each of the loans. */
    @Query("select a from LoanCollectionActivity a where a.tenantId = :tenantId and a.loanId in :loanIds"
            + " and a.createdAt = (select max(b.createdAt) from LoanCollectionActivity b"
            + " where b.tenantId = a.tenantId and b.loanId = a.loanId)")
    List<LoanCollectionActivity> findLatest(@Param("tenantId") UUID tenantId,
                                            @Param("loanIds") Collection<UUID> loanIds);
}
