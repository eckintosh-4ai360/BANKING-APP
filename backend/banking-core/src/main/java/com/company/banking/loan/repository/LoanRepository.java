package com.company.banking.loan.repository;

import com.company.banking.loan.entity.Loan;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanRepository extends JpaRepository<Loan, UUID> {

    Optional<Loan> findByTenantIdAndId(UUID tenantId, UUID id);

    Optional<Loan> findByTenantIdAndApplicationId(UUID tenantId, UUID applicationId);

    List<Loan> findAllByTenantIdAndApplicationIdIn(UUID tenantId, Collection<UUID> applicationIds);

    /** Row lock for repayments, accrual and every status change of the loan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from Loan l where l.tenantId = :tenantId and l.id = :id")
    Optional<Loan> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    List<Loan> findAllByTenantIdAndCustomerIdOrderByDisbursedAtDesc(UUID tenantId, UUID customerId);

    @Query("""
            select l from Loan l
            where l.tenantId = :tenantId
              and (:allBranches = true or l.branchId in :branchIds)
              and (:customerId is null or l.customerId = :customerId)
              and (:status is null or l.status = :status)
            order by l.disbursedAt desc, l.id desc""")
    Page<Loan> search(@Param("tenantId") UUID tenantId, @Param("allBranches") boolean allBranches,
                      @Param("branchIds") Collection<UUID> branchIds, @Param("customerId") UUID customerId,
                      @Param("status") Loan.Status status, Pageable page);

    /** Active loans in arrears in the caller's branches, the longest overdue first (the collections queue). */
    @Query("""
            select l from Loan l
            where l.tenantId = :tenantId
              and (:allBranches = true or l.branchId in :branchIds)
              and l.status = com.company.banking.loan.entity.Loan.Status.ACTIVE
              and l.daysPastDue >= :minDays
            order by l.daysPastDue desc, l.id""")
    Page<Loan> inArrears(@Param("tenantId") UUID tenantId, @Param("allBranches") boolean allBranches,
                         @Param("branchIds") Collection<UUID> branchIds, @Param("minDays") int minDays,
                         Pageable page);

    /** Next batch (by id) of loans in a status, for end-of-day. */
    @Query("select l.id from Loan l where l.tenantId = :tenantId and l.status = :status and l.id > :after"
            + " order by l.id")
    List<UUID> idsWithStatus(@Param("tenantId") UUID tenantId, @Param("status") Loan.Status status,
                             @Param("after") UUID after, Limit limit);
}
