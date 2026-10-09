package com.company.banking.loan.repository;

import com.company.banking.loan.entity.LoanDelinquencyBand;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoanDelinquencyBandRepository extends JpaRepository<LoanDelinquencyBand, LoanDelinquencyBand.Key> {

    List<LoanDelinquencyBand> findAllByIdTenantIdOrderByMinDays(UUID tenantId);

    /** Runs at once (not at flush), so a replacement set can reuse the same codes and minimum days. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from LoanDelinquencyBand b where b.id.tenantId = :tenantId")
    int deleteAllOfTenant(@Param("tenantId") UUID tenantId);
}
