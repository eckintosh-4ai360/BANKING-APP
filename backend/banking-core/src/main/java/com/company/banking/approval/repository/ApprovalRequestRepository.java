package com.company.banking.approval.repository;

import com.company.banking.approval.entity.ApprovalRequest;
import com.company.banking.approval.model.ApprovalStatus;
import com.company.banking.approval.model.ApprovalType;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID>,
        JpaSpecificationExecutor<ApprovalRequest> {

    Optional<ApprovalRequest> findByTenantIdAndId(UUID tenantId, UUID id);

    /**
     * Row lock for deciding, so two checkers cannot both approve.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ApprovalRequest r where r.tenantId = :tenantId and r.id = :id")
    Optional<ApprovalRequest> lockByTenantIdAndId(@Param("tenantId") UUID tenantId, @Param("id") UUID id);

    boolean existsByTenantIdAndRequestTypeAndResourceIdAndStatus(UUID tenantId, ApprovalType requestType,
                                                                 UUID resourceId, ApprovalStatus status);
}
