package com.company.banking.susu.repository;

import com.company.banking.susu.entity.SusuContribution;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SusuContributionRepository extends JpaRepository<SusuContribution, UUID> {

    List<SusuContribution> findAllByTenantIdAndPlanIdOrderBySequenceNo(UUID tenantId, UUID planId);

    /** Unpaid contributions (expected or missed), oldest first: what a payment settles. */
    @Query("""
            select c from SusuContribution c
            where c.tenantId = :tenantId and c.planId = :planId
              and c.status in (com.company.banking.susu.entity.SusuContribution.Status.EXPECTED,
                               com.company.banking.susu.entity.SusuContribution.Status.MISSED)
            order by c.sequenceNo""")
    List<SusuContribution> findUnpaid(@Param("tenantId") UUID tenantId, @Param("planId") UUID planId);

    @Query("""
            select c from SusuContribution c
            where c.tenantId = :tenantId and c.planId = :planId and c.dueDate <= :date
              and c.status = com.company.banking.susu.entity.SusuContribution.Status.EXPECTED
            order by c.sequenceNo""")
    List<SusuContribution> findExpectedDueBy(@Param("tenantId") UUID tenantId, @Param("planId") UUID planId,
                                             @Param("date") LocalDate date);

    List<SusuContribution> findAllByTenantIdAndPlanIdAndCycleNoOrderBySequenceNo(UUID tenantId, UUID planId,
                                                                                 int cycleNo);
}
