package com.company.banking.operations.repository;

import com.company.banking.operations.entity.EodStepRecord;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EodStepRepository extends JpaRepository<EodStepRecord, EodStepRecord.Key> {

    @Query("select s from EodStepRecord s where s.tenantId = :tenantId and s.id.runId = :runId order by s.stepOrder")
    List<EodStepRecord> findOfRun(@Param("tenantId") UUID tenantId, @Param("runId") UUID runId);
}
